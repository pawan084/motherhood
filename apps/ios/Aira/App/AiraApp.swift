import SwiftUI

@main
struct AiraApp: App {
    @StateObject private var state = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(state)
                .tint(.airaAubergine)
        }
    }
}
