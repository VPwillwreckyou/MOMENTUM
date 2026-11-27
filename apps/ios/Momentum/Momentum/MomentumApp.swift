import SwiftUI

@main
struct MomentumApp: App {
    @StateObject private var store = MomentumStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
        }
    }
}
