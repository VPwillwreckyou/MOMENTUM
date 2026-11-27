import Foundation

/// A small, realistic starter workspace. Only written on first launch or when
/// the user chooses "Restore sample workspace" in Settings.
func makeSeedWorkspace(now: Date = Date()) -> Workspace {
    let stamp = isoString(now)
    let calendar = Calendar.current
    func day(_ offset: Int) -> String {
        dateOnlyString(calendar.date(byAdding: .day, value: offset, to: now) ?? now)
    }
    func ago(_ days: Int) -> String {
        isoString(calendar.date(byAdding: .day, value: -days, to: now) ?? now)
    }

    let work = Category(
        id: "cat-work", name: "Work", color: "#3b82f6", icon: .work,
        createdAt: stamp, updatedAt: stamp, deletedAt: nil
    )
    let health = Category(
        id: "cat-health", name: "Health", color: "#34d399", icon: .health,
        createdAt: stamp, updatedAt: stamp, deletedAt: nil
    )
    let learning = Category(
        id: "cat-learning", name: "Learning", color: "#8b5cf6", icon: .learning,
        createdAt: stamp, updatedAt: stamp, deletedAt: nil
    )

    func milestone(
        id: String, title: String, due: Int, completed: Bool, order: Int
    ) -> Milestone {
        Milestone(
            id: id, title: title, description: nil, dueDate: day(due),
            completed: completed, completedAt: completed ? ago(2) : nil, order: order
        )
    }

    func task(
        id: String, goalID: String, title: String, priority: Priority = .medium,
        due: Int? = nil, completed: Bool = false, completedDaysAgo: Int = 1,
        milestoneID: String? = nil, parentID: String? = nil, order: Int
    ) -> GoalTask {
        GoalTask(
            id: id, goalId: goalID, milestoneId: milestoneID, parentTaskId: parentID,
            title: title, notes: nil, completed: completed,
            completedAt: completed ? isoString(calendar.date(byAdding: .day, value: -completedDaysAgo, to: now) ?? now) : nil,
            dueDate: due.map { day($0) }, priority: priority, order: order,
            createdAt: stamp, updatedAt: stamp
        )
    }

    let appGoal = Goal(
        id: "goal-ios-launch", title: "Ship the MOMENTUM iOS app",
        description: "Build a native iPhone experience that keeps every goal available offline.",
        notes: "Focus on the core loop: capture a goal, see the next move, and mark progress.",
        categoryId: work.id, status: .active, kind: .oneTime, recurrence: nil,
        priority: .high, startDate: day(-6), deadline: day(12),
        createdAt: ago(6), updatedAt: stamp, completedAt: nil, archivedAt: nil, deletedAt: nil,
        seriesId: nil, occurrence: 1,
        milestones: [
            milestone(id: "ms-spec", title: "Define the core screens", due: -3, completed: true, order: 0),
            milestone(id: "ms-build", title: "Build the goal workspace", due: 5, completed: false, order: 1),
            milestone(id: "ms-polish", title: "Polish and test on device", due: 12, completed: false, order: 2),
        ],
        tasks: [
            task(id: "t-spec", goalID: "goal-ios-launch", title: "Map the data model", priority: .high, due: -5, completed: true, milestoneID: "ms-spec", order: 0),
            task(id: "t-ui", goalID: "goal-ios-launch", title: "Sketch the overview screen", due: -2, completed: true, milestoneID: "ms-spec", order: 1),
            task(id: "t-store", goalID: "goal-ios-launch", title: "Add local JSON persistence", priority: .critical, due: 0, milestoneID: "ms-build", order: 2),
            task(id: "t-detail", goalID: "goal-ios-launch", title: "Build goal detail and task lists", priority: .high, due: 2, milestoneID: "ms-build", order: 3),
            task(id: "t-sub", goalID: "goal-ios-launch", title: "Add subtasks under goal detail", due: 3, parentID: "t-detail", order: 4),
            task(id: "t-test", goalID: "goal-ios-launch", title: "Run unit tests and device pass", due: 10, milestoneID: "ms-polish", order: 5),
        ],
        finance: nil
    )

    let runGoal = Goal(
        id: "goal-10k", title: "Run a 10K without stopping",
        description: "Build up distance gradually and keep the easy runs easy.",
        notes: "Three runs a week. Long run on Sunday.",
        categoryId: health.id, status: .active, kind: .oneTime, recurrence: nil,
        priority: .medium, startDate: day(-14), deadline: day(30),
        createdAt: ago(14), updatedAt: stamp, completedAt: nil, archivedAt: nil, deletedAt: nil,
        seriesId: nil, occurrence: 1,
        milestones: [
            milestone(id: "ms-5k", title: "Comfortable 5K", due: 7, completed: false, order: 0),
            milestone(id: "ms-10k", title: "10K goal race", due: 30, completed: false, order: 1),
        ],
        tasks: [
            task(id: "t-run-1", goalID: "goal-10k", title: "Easy 4K run", due: -1, completed: true, completedDaysAgo: 1, milestoneID: "ms-5k", order: 0),
            task(id: "t-run-2", goalID: "goal-10k", title: "Interval session", due: 1, milestoneID: "ms-5k", order: 1),
            task(id: "t-run-3", goalID: "goal-10k", title: "Long run 7K", due: 6, milestoneID: "ms-5k", order: 2),
        ],
        finance: nil
    )

    let readingGoal = Goal(
        id: "goal-reading", title: "Read 12 books this year",
        description: "One book a month, with a short note after each one.",
        notes: "",
        categoryId: learning.id, status: .active, kind: .recurring,
        recurrence: Recurrence(frequency: .monthly, interval: 1), priority: .low,
        startDate: nil, deadline: day(28),
        createdAt: ago(20), updatedAt: stamp, completedAt: nil, archivedAt: nil, deletedAt: nil,
        seriesId: nil, occurrence: 1,
        milestones: [],
        tasks: [
            task(id: "t-read", goalID: "goal-reading", title: "Pick the next book", priority: .low, due: 3, order: 0),
        ],
        finance: nil
    )

    let doneGoal = Goal(
        id: "goal-setup", title: "Set up the workspace",
        description: "A first small win to get the momentum loop going.",
        notes: "Archive or delete this once you have your own goals.",
        categoryId: work.id, status: .completed, kind: .oneTime, recurrence: nil,
        priority: .low, startDate: day(-10), deadline: day(-8),
        createdAt: ago(11), updatedAt: stamp, completedAt: ago(8),
        archivedAt: nil, deletedAt: nil, seriesId: nil, occurrence: 1,
        milestones: [],
        tasks: [
            task(id: "t-setup-1", goalID: "goal-setup", title: "Open MOMENTUM", completed: true, completedDaysAgo: 9, order: 0),
            task(id: "t-setup-2", goalID: "goal-setup", title: "Create a category", completed: true, completedDaysAgo: 8, order: 1),
        ],
        finance: nil
    )

    let activities = [
        Activity(id: UUID().uuidString, goalId: appGoal.id, type: .taskCompleted, message: "Sketch the overview screen", at: ago(2)),
        Activity(id: UUID().uuidString, goalId: runGoal.id, type: .taskCompleted, message: "Easy 4K run", at: ago(1)),
        Activity(id: UUID().uuidString, goalId: appGoal.id, type: .goalCreated, message: "Ship the MOMENTUM iOS app", at: ago(6)),
    ]

    return Workspace(
        schemaVersion: 1, installationId: UUID().uuidString,
        goals: [appGoal, runGoal, readingGoal, doneGoal],
        categories: [work, health, learning], activities: activities,
        createdAt: ago(6), updatedAt: stamp
    )
}
