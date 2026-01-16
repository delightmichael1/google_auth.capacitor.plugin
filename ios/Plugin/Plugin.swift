import Foundation
import Capacitor
import GoogleSignIn

@objc(GoogleAuth)
public class GoogleAuth: CAPPlugin {
    var signInCall: CAPPluginCall?
    var googleSignIn: GIDSignIn!
    var googleSignInConfiguration: GIDConfiguration!
    var forceAuthCode: Bool = false
    var additionalScopes: [String] = []

    func loadSignInClient(
        customClientId: String,
        customScopes: [String]
    ) {
        googleSignIn = GIDSignIn.sharedInstance
        
        let serverClientId = getServerClientIdValue()
        
        googleSignInConfiguration = GIDConfiguration(
            clientID: customClientId,
            serverClientID: serverClientId
        )
        
        // These are scopes granted by default by the signIn method
        let defaultGrantedScopes = ["email", "profile", "openid"]
        
        // Filter out default scopes to get additional scopes
        additionalScopes = customScopes.filter { scope in
            !defaultGrantedScopes.contains(scope.lowercased())
        }
        
        // Get forceAuthCode from config
        forceAuthCode = getConfig().getBoolean("forceCodeForRefreshToken", false)
        
        // Register for URL handling
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleOpenUrl(_:)),
            name: Notification.Name(Notification.Name.capacitorOpenURL.rawValue),
            object: nil
        )
    }

    public override func load() {
        // Plugin loaded
    }

    @objc
    func initialize(_ call: CAPPluginCall) {
        // Get client ID from initialize call or config file
        guard let clientId = call.getString("clientId") ?? getClientIdValue() else {
            call.reject("No client ID found. Please provide clientId in initialize() or config.")
            return
        }

        // Get scopes from initialize call or config file
        let customScopes = call.getArray("scopes", String.self) ?? 
            (getConfigValue("scopes") as? [String] ?? ["email", "profile"])

        // Get force auth code from initialize call or config file
        forceAuthCode = call.getBool("grantOfflineAccess") ?? 
            (getConfigValue("forceCodeForRefreshToken") as? Bool ?? false)
        
        // Load the sign-in client
        loadSignInClient(
            customClientId: clientId,
            customScopes: customScopes
        )
        
        call.resolve()
    }

    @objc
    func signIn(_ call: CAPPluginCall) {
        signInCall = call
        
        DispatchQueue.main.async {
            // Check if we should restore previous sign-in
            let shouldRestorePrevious = self.googleSignIn.hasPreviousSignIn() && !self.forceAuthCode
            
            if shouldRestorePrevious {
                self.googleSignIn.restorePreviousSignIn { [weak self] user, error in
                    guard let self = self else { return }
                    
                    if let error = error {
                        self.signInCall?.reject(
                            error.localizedDescription,
                            "\((error as NSError).code)",
                            error
                        )
                        return
                    }
                    
                    guard let user = user else {
                        self.signInCall?.reject("Sign-in failed: No user returned")
                        return
                    }
                    
                    // Check if we need additional scopes
                    if !self.additionalScopes.isEmpty {
                        self.requestAdditionalScopes(for: user)
                    } else {
                        self.resolveSignInCall(with: user)
                    }
                }
            } else {
                guard let presentingVc = self.bridge?.viewController else {
                    call.reject("Unable to get view controller")
                    return
                }
                
                self.googleSignIn.signIn(
                    with: self.googleSignInConfiguration,
                    presenting: presentingVc,
                    hint: nil,
                    additionalScopes: self.additionalScopes
                ) { [weak self] user, error in
                    guard let self = self else { return }
                    
                    if let error = error {
                        let nsError = error as NSError
                        
                        // Check for user cancellation
                        if nsError.code == -5 {
                            self.signInCall?.reject(
                                "The user canceled the sign-in flow.",
                                "USER_CANCELLED",
                                error
                            )
                        } else {
                            self.signInCall?.reject(
                                error.localizedDescription,
                                "\(nsError.code)",
                                error
                            )
                        }
                        return
                    }
                    
                    guard let user = user else {
                        self.signInCall?.reject("Sign-in failed: No user returned")
                        return
                    }
                    
                    self.resolveSignInCall(with: user)
                }
            }
        }
    }
    
    private func requestAdditionalScopes(for user: GIDGoogleUser) {
        guard let presentingVc = self.bridge?.viewController else {
            signInCall?.reject("Unable to get view controller")
            return
        }
        
        self.googleSignIn.addScopes(
            self.additionalScopes,
            presenting: presentingVc
        ) { [weak self] user, error in
            guard let self = self else { return }
            
            if let error = error {
                self.signInCall?.reject(
                    error.localizedDescription,
                    "\((error as NSError).code)",
                    error
                )
                return
            }
            
            guard let user = user else {
                self.signInCall?.reject("Failed to add scopes: No user returned")
                return
            }
            
            self.resolveSignInCall(with: user)
        }
    }

    @objc
    func refresh(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let currentUser = self.googleSignIn.currentUser else {
                call.reject("User not logged in.")
                return
            }
            
            currentUser.authentication.do { authentication, error in
                if let error = error {
                    call.reject(
                        error.localizedDescription,
                        "\((error as NSError).code)",
                        error
                    )
                    return
                }
                
                guard let authentication = authentication else {
                    call.reject("Failed to refresh: No authentication returned")
                    return
                }
                
                let authenticationData: [String: Any] = [
                    "accessToken": authentication.accessToken,
                    "idToken": authentication.idToken ?? "",
                    "refreshToken": authentication.refreshToken ?? ""
                ]
                
                call.resolve(authenticationData)
            }
        }
    }

    @objc
    func signOut(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if self.googleSignIn != nil {
                self.googleSignIn.signOut()
            }
            call.resolve()
        }
    }

    @objc
    func handleOpenUrl(_ notification: Notification) {
        guard let object = notification.object as? [String: Any],
              let url = object["url"] as? URL else {
            return
        }
        
        googleSignIn.handle(url)
    }
    
    func getClientIdValue() -> String? {
        // Priority: iosClientId > clientId > GoogleService-Info.plist
        if let clientId = getConfig().getString("iosClientId") {
            return clientId
        }
        
        if let clientId = getConfig().getString("clientId") {
            return clientId
        }
        
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let dict = NSDictionary(contentsOfFile: path) as? [String: AnyObject],
           let clientId = dict["CLIENT_ID"] as? String {
            return clientId
        }
        
        return nil
    }
    
    func getServerClientIdValue() -> String? {
        return getConfig().getString("serverClientId")
    }

    func resolveSignInCall(with user: GIDGoogleUser) {
        var userData: [String: Any] = [
            "authentication": [
                "accessToken": user.authentication.accessToken,
                "idToken": user.authentication.idToken ?? "",
                "refreshToken": user.authentication.refreshToken ?? ""
            ],
            "serverAuthCode": user.serverAuthCode ?? "",
            "email": user.profile?.email ?? "",
            "familyName": user.profile?.familyName ?? "",
            "givenName": user.profile?.givenName ?? "",
            "id": user.userID ?? "",
            "name": user.profile?.name ?? ""
        ]
        
        if let imageUrl = user.profile?.imageURL(withDimension: 100)?.absoluteString {
            userData["imageUrl"] = imageUrl
        } else {
            userData["imageUrl"] = ""
        }
        
        signInCall?.resolve(userData)
    }
}