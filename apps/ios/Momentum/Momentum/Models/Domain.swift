import Foundation

// Mirrors the local-first workspace model used by the web client
// (apps/web/src/state/types.ts). The native app uses the same concepts and
// field names, but stores them in a JSON file in Application Support.

enum Priority: String, Codable, CaseIterable, Identifiable {
    case low, medium, high, critical

    var id: String { rawValue }

    var label: String {
        switch self {
        case .low: return "Low"
        case .medium: return "Medium"
        case .high: return "High"
        case .critical: return "Critical"
        }
    }

    var rank: Int {
        switch self {
        case .critical: return 0
        case .high: return 1
        case .medium: return 2
        case .low: return 3
        }
    }
}

enum GoalStatus: String, Codable {
    case active, completed
}

enum GoalKind: String, Codable {
    case oneTime = "one-time"
    case recurring

    var label: String {
        switch self {
        case .oneTime: return "One-time"
        case .recurring: return "Recurring"
        }
    }
}

enum RecurrenceFrequency: String, Codable, CaseIterable, Identifiable {
    case daily, weekly, monthly

    var id: String { rawValue }

    var label: String { rawValue.capitalized }
}

struct Recurrence: Codable, Hashable {
    var frequency: RecurrenceFrequency
    var interval: Int

    var label: String {
        let unit = frequency.rawValue
        guard interval == 1 else { return "Repeats every \(interval) \(unit)s" }
        return "Repeats every \(unit)"
    }
}

struct Milestone: Codable, Identifiable, Hashable {
    var id: String
    var title: String
    var description: String?
    var dueDate: String?
    var completed: Bool
    var completedAt: String?
    var order: Int
}

// Named GoalTask to avoid shadowing Swift's concurrency Task type.
struct GoalTask: Codable, Identifiable, Hashable {
    var id: String
    var goalId: String
    var milestoneId: String?
    var parentTaskId: String?
    var title: String
    var notes: String?
    var completed: Bool
    var completedAt: String?
    var dueDate: String?
    var priority: Priority
    var order: Int
    var createdAt: String
    var updatedAt: String
}

struct FinancePlan: Codable, Hashable {
    var enabled: Bool
    var currency: String
    var targetAmount: Double
    var currentAmount: Double
    var note: String?
}

struct Goal: Codable, Identifiable, Hashable {
    var id: String
    var title: String
    var description: String
    var notes: String
    var categoryId: String?
    var status: GoalStatus
    var kind: GoalKind
    var recurrence: Recurrence?
    var priority: Priority
    var startDate: String?
    var deadline: String?
    var createdAt: String
    var updatedAt: String
    var completedAt: String?
    var archivedAt: String?
    var deletedAt: String?
    var seriesId: String?
    var occurrence: Int?
    var milestones: [Milestone]
    var tasks: [GoalTask]
    var finance: FinancePlan?
    var aiGenerated: Bool?

    var isTrashed: Bool { deletedAt != nil }
    var isArchived: Bool { archivedAt != nil && deletedAt == nil }
    var isActive: Bool { status == .active && !isTrashed && !isArchived }
}

enum CategoryIcon: String, Codable, CaseIterable, Identifiable {
    case target, work, health, learning, creative, finance, home, relationships

    var id: String { rawValue }

    var label: String { rawValue.capitalized }

    var symbolName: String {
        switch self {
        case .target: return "target"
        case .work: return "briefcase.fill"
        case .health: return "heart.fill"
        case .learning: return "book.closed.fill"
        case .creative: return "paintbrush.fill"
        case .finance: return "dollarsign.circle.fill"
        case .home: return "house.fill"
        case .relationships: return "person.2.fill"
        }
    }
}

struct Category: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var color: String
    var icon: CategoryIcon
    var createdAt: String
    var updatedAt: String
    var deletedAt: String?
}

enum ActivityType: String, Codable {
    case goalCreated = "goal-created"
    case goalEdited = "goal-edited"
    case goalCompleted = "goal-completed"
    case goalArchived = "goal-archived"
    case goalRestored = "goal-restored"
    case taskCreated = "task-created"
    case taskCompleted = "task-completed"
    case taskReopened = "task-reopened"
    case milestoneCompleted = "milestone-completed"
    case noteUpdated = "note-updated"
}

struct Activity: Codable, Identifiable, Hashable {
    var id: String
    var goalId: String
    var type: ActivityType
    var message: String
    var at: String
}

enum ThemeMode: String, Codable, CaseIterable, Identifiable {
    case dark, light, system

    var id: String { rawValue }

    var label: String { rawValue.capitalized }
}

enum Density: String, Codable, CaseIterable, Identifiable {
    case comfortable, compact

    var id: String { rawValue }

    var label: String { rawValue.capitalized }
}

struct AppSettings: Codable {
    var schemaVersion: Int = 1
    var themeMode: ThemeMode = .dark
    var accent: String = "#3b82f6"
    var accentSecondary: String = "#8b5cf6"
    var density: Density = .comfortable
    var reduceMotion: Bool = false
    var weekStartsOn: Int = 1
    var displayName: String = ""
    var onboardingComplete: Bool = false

    enum CodingKeys: String, CodingKey {
        case schemaVersion, themeMode, accent, accentSecondary, density
        case reduceMotion, weekStartsOn, displayName, onboardingComplete
    }
}

struct Workspace: Codable {
    var schemaVersion: Int = 1
    var installationId: String
    var goals: [Goal]
    var categories: [Category]
    var activities: [Activity]
    var createdAt: String
    var updatedAt: String

    enum CodingKeys: String, CodingKey {
        case schemaVersion, installationId, goals, categories, activities
        case createdAt, updatedAt
    }
}

enum AccentPreset: Identifiable {
    case preset(name: String, hex: String, secondary: String)

    var id: String {
        switch self {
        case let .preset(name, hex, _): return name + hex
        }
    }

    var name: String {
        switch self {
        case let .preset(name, _, _): return name
        }
    }

    var hex: String {
        switch self {
        case let .preset(_, hex, _): return hex
        }
    }

    var secondary: String {
        switch self {
        case let .preset(_, _, secondary): return secondary
        }
    }

    static let all: [AccentPreset] = [
        .preset(name: "Signal Blue", hex: "#3b82f6", secondary: "#8b5cf6"),
        .preset(name: "Ion Cyan", hex: "#22d3ee", secondary: "#3b82f6"),
        .preset(name: "Volt Lime", hex: "#a3e635", secondary: "#34d399"),
        .preset(name: "Pulse Magenta", hex: "#f472b6", secondary: "#8b5cf6"),
        .preset(name: "Ember Orange", hex: "#fb923c", secondary: "#f87171"),
        .preset(name: "Ultraviolet", hex: "#8b5cf6", secondary: "#f472b6"),
        .preset(name: "Forest", hex: "#34d399", secondary: "#22d3ee"),
        .preset(name: "Crimson", hex: "#f87171", secondary: "#fb923c"),
    ]
}
