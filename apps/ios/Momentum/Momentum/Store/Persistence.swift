import Foundation
import SwiftUI
import UniformTypeIdentifiers

/// Local-only JSON persistence in Application Support.
/// The web client uses IndexedDB; this native app deliberately keeps its own
/// workspace so it works entirely offline with no service or account.
final class Persistence {
    let directory: URL

    var workspaceURL: URL { directory.appendingPathComponent("momentum-workspace.json") }
    var settingsURL: URL { directory.appendingPathComponent("momentum-settings.json") }

    init(directory: URL? = nil) {
        let base = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("MOMENTUM", isDirectory: true)
        self.directory = base
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
    }

    func loadWorkspace() -> Workspace? {
        guard let data = try? Data(contentsOf: workspaceURL) else { return nil }
        return try? JSONDecoder().decode(Workspace.self, from: data)
    }

    func loadSettings() -> AppSettings? {
        guard let data = try? Data(contentsOf: settingsURL) else { return nil }
        return try? JSONDecoder().decode(AppSettings.self, from: data)
    }

    func saveWorkspace(_ workspace: Workspace) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(workspace).write(to: workspaceURL, options: .atomic)
    }

    func saveSettings(_ settings: AppSettings) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(settings).write(to: settingsURL, options: .atomic)
    }

    func removeAll() throws {
        for url in [workspaceURL, settingsURL] {
            if FileManager.default.fileExists(atPath: url.path) {
                try FileManager.default.removeItem(at: url)
            }
        }
    }

    // MARK: - Backup

    private struct BackupEnvelope: Codable {
        let app: String
        let version: Int
        let exportedAt: String
        let workspace: Workspace
        let settings: AppSettings
    }

    func exportBackup(workspace: Workspace, settings: AppSettings) throws -> Data {
        let envelope = BackupEnvelope(
            app: "momentum",
            version: 2,
            exportedAt: isoString(),
            workspace: workspace,
            settings: settings
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(envelope)
    }

    func importBackup(_ data: Data) throws -> (workspace: Workspace, settings: AppSettings) {
        let envelope = try JSONDecoder().decode(BackupEnvelope.self, from: data)
        guard envelope.app == "momentum" else { throw PersistenceError.unrecognizedBackup }
        var workspace = envelope.workspace
        workspace.updatedAt = isoString()
        return (normalize(workspace), envelope.settings)
    }

    private func normalize(_ workspace: Workspace) -> Workspace {
        var copy = workspace
        copy.installationId = copy.installationId.isEmpty ? UUID().uuidString : copy.installationId
        copy.goals = copy.goals.map { goal in
            var item = goal
            item.priority = Priority(rawValue: item.priority.rawValue) ?? .medium
            item.status = GoalStatus(rawValue: item.status.rawValue) ?? .active
            item.kind = GoalKind(rawValue: item.kind.rawValue) ?? .oneTime
            if let recurrence = item.recurrence {
                item.recurrence = Recurrence(
                    frequency: RecurrenceFrequency(rawValue: recurrence.frequency.rawValue) ?? .weekly,
                    interval: min(52, max(1, recurrence.interval))
                )
            }
            return item
        }
        copy.categories = copy.categories.map { category in
            var item = category
            item.name = String(item.name.prefix(60))
            item.color = String(item.color.prefix(9))
            item.icon = CategoryIcon(rawValue: item.icon.rawValue) ?? .target
            return item
        }
        if copy.activities.count > 350 { copy.activities = Array(copy.activities.suffix(350)) }
        return copy
    }
}

enum PersistenceError: LocalizedError {
    case unrecognizedBackup

    var errorDescription: String? {
        switch self {
        case .unrecognizedBackup: return "That file is not a MOMENTUM backup."
        }
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    static var writableContentTypes: [UTType] { [.json] }

    var data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.data = data
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
