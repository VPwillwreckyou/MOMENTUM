import Foundation
import Combine

struct GoalDraft {
    var title: String
    var description: String
    var notes: String
    var categoryID: String?
    var kind: GoalKind
    var recurrence: Recurrence?
    var priority: Priority
    var startDate: String?
    var deadline: String?
}

/// Single source of truth for the native app. Every mutation writes the JSON
/// workspace atomically, so a killed app never loses a completed task.
final class MomentumStore: ObservableObject {
    @Published private(set) var workspace: Workspace
    @Published private(set) var settings: AppSettings
    @Published var storageError: String?

    private let persistence: Persistence

    init(directory: URL? = nil, seedIfMissing: Bool = true) {
        self.persistence = Persistence(directory: directory)
        let storedWorkspace = persistence.loadWorkspace()
        let storedSettings = persistence.loadSettings()
        if let storedWorkspace {
            self.workspace = storedWorkspace
        } else {
            self.workspace = seedIfMissing ? makeSeedWorkspace() : Workspace(
                schemaVersion: 1, installationId: UUID().uuidString,
                goals: [], categories: [], activities: [],
                createdAt: isoString(), updatedAt: isoString()
            )
        }
        self.settings = storedSettings ?? AppSettings()

        if storedWorkspace == nil || storedSettings == nil {
            persistAll()
        }
    }

    // MARK: - Derived data

    var activeGoals: [Goal] {
        workspace.goals.filter(\.isActive).sorted {
            if $0.priority.rank != $1.priority.rank { return $0.priority.rank < $1.priority.rank }
            return ($0.deadline ?? "9999") < ($1.deadline ?? "9999")
        }
    }

    var archivedGoals: [Goal] { workspace.goals.filter(\.isArchived) }
    var trashedGoals: [Goal] { workspace.goals.filter(\.isTrashed) }
    var liveCategories: [Category] { workspace.categories.filter { $0.deletedAt == nil } }

    func goal(id: String) -> Goal? { workspace.goals.first { $0.id == id } }
    func category(id: String?) -> Category? { guard let id else { return nil }; return workspace.categories.first { $0.id == id } }

    // MARK: - Settings

    func updateSettings(_ change: (inout AppSettings) -> Void) {
        var copy = settings
        change(&copy)
        settings = copy
        do { try persistence.saveSettings(settings) } catch { storageError = error.localizedDescription }
    }

    // MARK: - Goals

    func addGoal(_ draft: GoalDraft) -> Goal {
        let stamp = isoString()
        let goal = Goal(
            id: UUID().uuidString, title: draft.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: draft.description, notes: draft.notes, categoryId: draft.categoryID,
            status: .active, kind: draft.kind,
            recurrence: draft.kind == .recurring ? (draft.recurrence ?? Recurrence(frequency: .weekly, interval: 1)) : nil,
            priority: draft.priority, startDate: draft.startDate, deadline: draft.deadline,
            createdAt: stamp, updatedAt: stamp, completedAt: nil, archivedAt: nil, deletedAt: nil,
            seriesId: nil, occurrence: 1, milestones: [], tasks: [], finance: nil, aiGenerated: nil
        )
        workspace.goals.append(goal)
        log(goalID: goal.id, type: .goalCreated, message: goal.title)
        saveWorkspace()
        return goal
    }

    func updateGoal(_ goal: Goal) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == goal.id }) else { return }
        var copy = goal
        copy.updatedAt = isoString()
        workspace.goals[index] = copy
        log(goalID: copy.id, type: .goalEdited, message: copy.title)
        saveWorkspace()
    }

    func setGoalStatus(_ id: String, completed: Bool) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == id }) else { return }
        let original = workspace.goals[index]
        guard original.status != (completed ? .completed : .active) else { return }
        // Recurring occurrences are not reopened; the next one is already created.
        if !completed && original.kind == .recurring { return }

        var copy = original
        copy.status = completed ? .completed : .active
        copy.completedAt = completed ? isoString() : nil
        copy.updatedAt = isoString()
        workspace.goals[index] = copy
        log(goalID: id, type: completed ? .goalCompleted : .goalRestored, message: copy.title)

        if completed, let next = nextOccurrence(from: copy) {
            workspace.goals.append(next)
        }
        saveWorkspace()
    }

    func archiveGoal(_ id: String, archived: Bool) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == id }) else { return }
        var copy = workspace.goals[index]
        copy.archivedAt = archived ? isoString() : nil
        copy.deletedAt = nil
        copy.updatedAt = isoString()
        workspace.goals[index] = copy
        log(goalID: id, type: archived ? .goalArchived : .goalRestored, message: copy.title)
        saveWorkspace()
    }

    func trashGoal(_ id: String) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == id }) else { return }
        workspace.goals[index].deletedAt = isoString()
        workspace.goals[index].archivedAt = nil
        workspace.goals[index].updatedAt = isoString()
        saveWorkspace()
    }

    func restoreGoal(_ id: String) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == id }) else { return }
        workspace.goals[index].deletedAt = nil
        workspace.goals[index].updatedAt = isoString()
        saveWorkspace()
    }

    func deleteGoalForever(_ id: String) {
        workspace.goals.removeAll { $0.id == id }
        workspace.activities.removeAll { $0.goalId == id }
        saveWorkspace()
    }

    func emptyTrash() {
        let ids = Set(trashedGoals.map(\.id))
        workspace.goals.removeAll { ids.contains($0.id) }
        workspace.activities.removeAll { ids.contains($0.goalId) }
        saveWorkspace()
    }

    func purgeExpiredTrash(now: Date = Date()) {
        guard let cutoff = Calendar.current.date(byAdding: .day, value: -30, to: now) else { return }
        let expired = workspace.goals.filter { goal in
            guard let deleted = goal.deletedAt.flatMap(parseTimestamp) else { return false }
            return deleted < cutoff
        }
        guard !expired.isEmpty else { return }
        let ids = Set(expired.map(\.id))
        workspace.goals.removeAll { ids.contains($0.id) }
        workspace.activities.removeAll { ids.contains($0.goalId) }
        saveWorkspace()
    }

    // MARK: - Tasks and milestones

    func addTask(goalID: String, title: String, priority: Priority = .medium, dueDate: String? = nil,
                 milestoneID: String? = nil, parentTaskID: String? = nil) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == goalID }) else { return }
        let stamp = isoString()
        let order = workspace.goals[index].tasks.map(\.order).max().map { $0 + 1 } ?? 0
        let task = GoalTask(
            id: UUID().uuidString, goalId: goalID, milestoneId: milestoneID, parentTaskId: parentTaskID,
            title: title.trimmingCharacters(in: .whitespacesAndNewlines), notes: nil,
            completed: false, completedAt: nil, dueDate: dueDate, priority: priority,
            order: order, createdAt: stamp, updatedAt: stamp
        )
        workspace.goals[index].tasks.append(task)
        workspace.goals[index].updatedAt = stamp
        log(goalID: goalID, type: .taskCreated, message: task.title)
        saveWorkspace()
    }

    func toggleTask(goalID: String, taskID: String) {
        guard let goalIndex = workspace.goals.firstIndex(where: { $0.id == goalID }),
              let taskIndex = workspace.goals[goalIndex].tasks.firstIndex(where: { $0.id == taskID }) else { return }
        var task = workspace.goals[goalIndex].tasks[taskIndex]
        task.completed.toggle()
        task.completedAt = task.completed ? isoString() : nil
        task.updatedAt = isoString()
        workspace.goals[goalIndex].tasks[taskIndex] = task
        workspace.goals[goalIndex].updatedAt = task.updatedAt
        log(goalID: goalID, type: task.completed ? .taskCompleted : .taskReopened, message: task.title)
        saveWorkspace()
    }

    func deleteTask(goalID: String, taskID: String) {
        guard let goalIndex = workspace.goals.firstIndex(where: { $0.id == goalID }) else { return }
        workspace.goals[goalIndex].tasks.removeAll { $0.id == taskID || $0.parentTaskId == taskID }
        workspace.goals[goalIndex].updatedAt = isoString()
        saveWorkspace()
    }

    func addMilestone(goalID: String, title: String, dueDate: String? = nil) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == goalID }) else { return }
        let order = workspace.goals[index].milestones.map(\.order).max().map { $0 + 1 } ?? 0
        let milestone = Milestone(
            id: UUID().uuidString, title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: nil, dueDate: dueDate, completed: false, completedAt: nil, order: order
        )
        workspace.goals[index].milestones.append(milestone)
        workspace.goals[index].updatedAt = isoString()
        saveWorkspace()
    }

    func toggleMilestone(goalID: String, milestoneID: String) {
        guard let goalIndex = workspace.goals.firstIndex(where: { $0.id == goalID }),
              let milestoneIndex = workspace.goals[goalIndex].milestones.firstIndex(where: { $0.id == milestoneID }) else { return }
        var milestone = workspace.goals[goalIndex].milestones[milestoneIndex]
        milestone.completed.toggle()
        milestone.completedAt = milestone.completed ? isoString() : nil
        workspace.goals[goalIndex].milestones[milestoneIndex] = milestone
        workspace.goals[goalIndex].updatedAt = isoString()
        if milestone.completed { log(goalID: goalID, type: .milestoneCompleted, message: milestone.title) }
        saveWorkspace()
    }

    func deleteMilestone(goalID: String, milestoneID: String) {
        guard let goalIndex = workspace.goals.firstIndex(where: { $0.id == goalID }) else { return }
        workspace.goals[goalIndex].milestones.removeAll { $0.id == milestoneID }
        workspace.goals[goalIndex].tasks.indices.forEach { index in
            if workspace.goals[goalIndex].tasks[index].milestoneId == milestoneID {
                workspace.goals[goalIndex].tasks[index].milestoneId = nil
            }
        }
        workspace.goals[goalIndex].updatedAt = isoString()
        saveWorkspace()
    }

    func updateNotes(goalID: String, notes: String) {
        guard let index = workspace.goals.firstIndex(where: { $0.id == goalID }) else { return }
        workspace.goals[index].notes = String(notes.prefix(10_000))
        workspace.goals[index].updatedAt = isoString()
        log(goalID: goalID, type: .noteUpdated, message: "Notes updated")
        saveWorkspace()
    }

    // MARK: - Categories

    func addCategory(name: String, color: String, icon: CategoryIcon) {
        let stamp = isoString()
        let category = Category(
            id: UUID().uuidString, name: String(name.prefix(60)), color: color, icon: icon,
            createdAt: stamp, updatedAt: stamp, deletedAt: nil
        )
        workspace.categories.append(category)
        saveWorkspace()
    }

    func updateCategory(_ category: Category) {
        guard let index = workspace.categories.firstIndex(where: { $0.id == category.id }) else { return }
        var copy = category
        copy.updatedAt = isoString()
        workspace.categories[index] = copy
        saveWorkspace()
    }

    func deleteCategory(_ id: String) {
        guard let index = workspace.categories.firstIndex(where: { $0.id == id }) else { return }
        workspace.categories[index].deletedAt = isoString()
        for goalIndex in workspace.goals.indices where workspace.goals[goalIndex].categoryId == id {
            workspace.goals[goalIndex].categoryId = nil
        }
        saveWorkspace()
    }

    // MARK: - Backup and reset

    func replaceWorkspace(_ restored: Workspace) {
        workspace = restored
        saveWorkspace()
    }

    func replaceAll(workspace newWorkspace: Workspace, settings newSettings: AppSettings) {
        workspace = newWorkspace
        settings = newSettings
        persistAll()
    }

    func restoreSampleWorkspace() {
        workspace = makeSeedWorkspace()
        saveWorkspace()
    }

    func eraseAll() {
        workspace = Workspace(
            schemaVersion: 1, installationId: UUID().uuidString, goals: [], categories: [],
            activities: [], createdAt: isoString(), updatedAt: isoString()
        )
        settings = AppSettings()
        settings.onboardingComplete = true
        do { try persistence.removeAll() } catch { storageError = error.localizedDescription }
        persistAll()
    }

    func exportBackupData() -> Data? {
        do { return try persistence.exportBackup(workspace: workspace, settings: settings) }
        catch { storageError = error.localizedDescription; return nil }
    }

    func importBackupData(_ data: Data) -> Bool {
        do {
            let restored = try persistence.importBackup(data)
            replaceAll(workspace: restored.workspace, settings: restored.settings)
            return true
        } catch {
            storageError = error.localizedDescription
            return false
        }
    }

    // MARK: - Internal

    private func log(goalID: String, type: ActivityType, message: String) {
        workspace.activities.append(Activity(
            id: UUID().uuidString, goalId: goalID, type: type,
            message: String(message.prefix(180)), at: isoString()
        ))
        if workspace.activities.count > 350 {
            workspace.activities = Array(workspace.activities.suffix(350))
        }
    }

    private func saveWorkspace() {
        workspace.updatedAt = isoString()
        do { try persistence.saveWorkspace(workspace) }
        catch { storageError = error.localizedDescription }
    }

    private func persistAll() {
        do {
            try persistence.saveWorkspace(workspace)
            try persistence.saveSettings(settings)
        } catch { storageError = error.localizedDescription }
    }
}
