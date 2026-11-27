import SwiftUI

struct PlannerView: View {
    @EnvironmentObject private var store: MomentumStore
    @EnvironmentObject private var nav: NavigationState

    @State private var selectedGoalID: String?
    @State private var showingTaskPrompt = false

    private var focus: [GoalTask] { focusTasks(store.workspace.goals, limit: 8) }
    private var weekly: [GoalTask] { tasksDueThisWeek(store.workspace.goals) }
    private var overdue: [GoalTask] {
        store.activeGoals.flatMap { goal in overdueTasks(in: goal) }
            .sorted { ($0.dueDate ?? "") < ($1.dueDate ?? "") }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Planner").font(.largeTitle.bold()).foregroundStyle(AppTheme.primaryText)
                    Text("The next moves, gathered in one place.")
                        .font(.subheadline).foregroundStyle(AppTheme.secondaryText)
                }

                plannerSection(title: "Focus queue", systemImage: "bolt.fill", tasks: focus, emptyTitle: "Queue is clear",
                               emptyMessage: "Add a task to a goal and it will show up here.")
                plannerSection(title: "Overdue", systemImage: "exclamationmark.triangle.fill", tasks: overdue,
                               emptyTitle: "Nothing overdue", emptyMessage: "You are keeping dates under control.")
                plannerSection(title: "Due this week", systemImage: "calendar", tasks: weekly,
                               emptyTitle: "No dates this week", emptyMessage: "Add a due date to a task to plan it.")
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("Planner")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    ForEach(store.activeGoals) { goal in
                        Button {
                            selectedGoalID = goal.id
                            showingTaskPrompt = true
                        } label: {
                            Label(goal.title, systemImage: "plus")
                        }
                    }
                    Divider()
                    Button { nav.newGoal() } label: { Label("New goal", systemImage: "target") }
                } label: {
                    Image(systemName: "plus.circle.fill")
                }
                .accessibilityLabel("Add task")
            }
        }
        .sheet(isPresented: $showingTaskPrompt) {
            TextPromptSheet(
                title: "New task", placeholder: "What needs to happen?", confirmTitle: "Add",
                showsPriority: true, showsDueDate: true
            ) { title, priority, due in
                if let goalID = selectedGoalID {
                    store.addTask(goalID: goalID, title: title, priority: priority, dueDate: due)
                }
            }
        }
    }

    private func plannerSection(title: String, systemImage: String, tasks: [GoalTask],
                                emptyTitle: String, emptyMessage: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: systemImage)
                .font(.headline).foregroundStyle(AppTheme.primaryText)
            if tasks.isEmpty {
                Text(emptyMessage)
                    .font(.subheadline).foregroundStyle(AppTheme.secondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .momentumCard()
            } else {
                VStack(spacing: 0) {
                    ForEach(tasks) { task in
                        TaskRow(goalID: task.goalId, task: task, showsGoal: true)
                        if task.id != tasks.last?.id {
                            Divider().background(AppTheme.line)
                        }
                    }
                }
                .padding(14)
                .momentumCard()
            }
        }
    }
}
