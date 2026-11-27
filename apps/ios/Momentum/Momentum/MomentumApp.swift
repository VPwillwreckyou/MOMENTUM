import SwiftUI

@main
struct MomentumApp: App {
    @StateObject private var store: MomentumStore

    init() {
        let isUITesting = ProcessInfo.processInfo.arguments.contains("--ui-testing")
        let testDirectory = isUITesting
            ? FileManager.default.temporaryDirectory
                .appendingPathComponent("MomentumUITests-\(ProcessInfo.processInfo.processIdentifier)", isDirectory: true)
            : nil
        _store = StateObject(wrappedValue: MomentumStore(directory: testDirectory, seedIfMissing: !isUITesting))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
        }
    }
}
