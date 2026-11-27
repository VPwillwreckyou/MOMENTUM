import SwiftUI

private enum DetailTab: String, CaseIterable, Identifiable {
    case plan, notes, activity
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}

private struct TaskPrompt: Identifiable {
    let id = UUID()
    let milestoneID: String?
    let parentTaskID: String?
}

struct GoalDetailView: View {
    @EnvironmentObject private var store: MomentumStore
    @EnvironmentObject private var nav: NavigationState

    let goalID: String

    @State private var tab: DetailTab = .plan
    @State private var showingCompleted = false
    @State private var taskPrompt: TaskPrompt?
    @State private var showingMilestonePrompt = false
    @State private var notesDraft = ""
    @State private var showingCompleteConfirm = false

    private var goal: Goal? { store.goal(id: goalID) }
    private var accent: Color { Color(hex: store.settings.accent) }

    var body: some View {
        Group {
            if let goal {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        overview(goal)
                        Picker("Section", selection: $tab) {
                            ForEach(DetailTab.allCases) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        switch tab {
                        case .plan: planSection(goal)
                        case .notes: notesSection(goal)
                        case .activity: activitySection(goal)
                        }
                    }
                    .padding(16)
                }
                .background(AppTheme.background.ignoresSafeArea())
            } else {
                EmptyState(
                    systemImage: "questionmark.circle",
                    title: "Goal not found",
                    message: "It may have been deleted. Return to the goals list."
                )
            }
        }
        .navigationTitle(goal?.title ?? "Goal")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbar }
        .sheet(item: $taskPrompt) { prompt in
            TextPromptSheet(
                title: prompt.parentTaskID == nil ? "New task" : "New subtask",
                placeholder: "What needs to happen?",
                confirmTitle: "Add",
                showsPriority: true,
                showsDueDate: true
            ) { title, priority, due in
                store.addTask(
                    goalID: goalID, title: title, priority: priority, dueDate: due,
                    milestoneID: prompt.milestoneID, parentTaskID: prompt.parentTaskID
                )
            }
        }
        .sheet(isPresented: $showingMilestonePrompt) {
            TextPromptSheet(
                title: "New milestone", placeholder: "A meaningful checkpoint",
                confirmTitle: "Add", showsDueDate: true
            ) { title, _, due in
                store.addMilestone(goalID: goalID, title: title, dueDate: due)
            }
        }
        .alert("Complete this goal?", isPresented: $showingCompleteConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Complete anyway") { completeGoal() }
        } message: {
            Text("Some tasks are still open. You can keep working on them after completion.")
        }
        .onAppear { notesDraft = goal?.notes ?? "" }
        .onChange(of: goalID) { _ in notesDraft = goal?.notes ?? "" }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Button { if let goal { nav.editGoal(goal) } } label: {
                    Label("Edit goal", systemImage: "pencil")
                }
                Button {
                    if let goal, goal.tasks.contains(where: { !$0.completed }), goal.status == .active {
                        showingCompleteConfirm = true
                    } else {
                        completeGoal()
                    }
                } label: {
                    Label(
                        goal?.status == .completed ? "Reopen goal" : "Complete goal",
                        systemImage: goal?.status == .completed ? "arrow.uturn.backward" : "checkmark.circle"
                    )
                }
                .disabled(goal?.kind == .recurring && goal?.status == .completed)
                Button {
                    if let goal { store.archiveGoal(goal.id, archived: !goal.isArchived) }
                } label: {
                    Label(goal?.isArchived == true ? "Unarchive" : "Archive",
                          systemImage: "archivebox")
                }
                Divider()
                Button(role: .destructive) {
                    store.trashGoal(goalID)
                    nav.path.removeAll()
                } label: {
                    Label("Move to trash", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
    }

    private func completeGoal() {
        guard let goal else { return }
        store.setGoalStatus(goal.id, completed: goal.status != .completed)
    }

    private func overview(_ goal: Goal) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 16) {
                ProgressRing(percent: goalPercent(goal), accent: accent, size: 92)
                VStack(alignment: .leading, spacing: 6) {
                    Text(goal.description.isEmpty ? "No description yet" : goal.description)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.secondaryText)
                    if let category = store.category(id: goal.categoryId) {
                        Label(category.name, systemImage: category.icon.symbolName)
                            .font(.caption)
                            .foregroundStyle(Color(hex: category.color))
                    }
                    Text(recurrenceLabel(goal))
                        .font(.caption)
                        .foregroundStyle(AppTheme.tertiaryText)
                    if let due = deadlineLabel(goal.deadline) {
                        Text(due)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(due.contains("overdue") ? AppTheme.danger : AppTheme.secondaryText)
                    }
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 10) {
                MetricTile(title: "Tasks", value: "\(goal.tasks.filter(\.completed).count)/\(goal.tasks.count)", systemImage: "checklist")
                MetricTile(title: "Milestones", value: "\(goal.milestones.filter(\.completed).count)/\(goal.milestones.count)", systemImage: "flag.fill")
                MetricTile(title: "Priority", value: goal.priority.label, systemImage: "exclamationmark.triangle.fill")
            }
            if let next = nextActionableTask(in: goal) {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Next move", systemImage: "arrow.right.circle.fill")
                        .font(.caption.weight(.semibold)).foregroundStyle(accent)
                    Text(next.title)
                        .font(.subheadline.weight(.semibold)).foregroundStyle(AppTheme.primaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(AppTheme.surfaceElevated, in: RoundedRectangle(cornerRadius: 14))
            }
        }
        .padding(16)
        .momentumCard()
    }

    private func planSection(_ goal: Goal) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            if !goal.milestones.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    SectionHeader(title: "Milestones")
                    ForEach(goal.milestones.sorted { $0.order < $1.order }) { milestone in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack(spacing: 10) {
                                Button {
                                    store.toggleMilestone(goalID: goal.id, milestoneID: milestone.id)
                                } label: {
                                    Image(systemName: milestone.completed ? "checkmark.circle.fill" : "flag")
                                        .foregroundStyle(milestone.completed ? AppTheme.success : accent)
                                }
                                .buttonStyle(.plain)
                                Text(milestone.title)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(milestone.completed ? AppTheme.tertiaryText : AppTheme.primaryText)
                                    .strikethrough(milestone.completed)
                                Spacer()
                                Button { taskPrompt = TaskPrompt(milestoneID: milestone.id, parentTaskID: nil) } label: {
                                    Image(systemName: "plus.circle")
                                }
                                .buttonStyle(.plain)
                            }
                            if let due = deadlineLabel(milestone.dueDate) {
                                Text(due).font(.caption).foregroundStyle(AppTheme.tertiaryText).padding(.leading, 30)
                            }
                            ForEach(goal.tasks.filter { $0.milestoneId == milestone.id && $0.parentTaskId == nil && (showingCompleted || !$0.completed) }) { task in
                                TaskBranchView(
                                    goal: goal, task: task, level: 1,
                                    showingCompleted: showingCompleted
                                ) { parentID in
                                    taskPrompt = TaskPrompt(milestoneID: milestone.id, parentTaskID: parentID)
                                }
                                .padding(.leading, 22)
                            }
                        }
                        .padding(12)
                        .background(AppTheme.surface, in: RoundedRectangle(cornerRadius: 14))
                    }
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                SectionHeader(title: "Tasks without a milestone")
                let standalone = goal.tasks.filter { $0.milestoneId == nil && $0.parentTaskId == nil && (showingCompleted || !$0.completed) }
                if standalone.isEmpty {
                    Text("No standalone tasks.").font(.subheadline).foregroundStyle(AppTheme.tertiaryText)
                }
                ForEach(standalone) { task in
                    TaskBranchView(goal: goal, task: task, level: 0, showingCompleted: showingCompleted) { parentID in
                        taskPrompt = TaskPrompt(milestoneID: nil, parentTaskID: parentID)
                    }
                }
                Button { taskPrompt = TaskPrompt(milestoneID: nil, parentTaskID: nil) } label: {
                    Label("Add task", systemImage: "plus")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.bordered)
            }

            let hiddenCount = goal.tasks.filter { !$0.completed }.count
            if hiddenCount < goal.tasks.count || showingCompleted {
                Button { showingCompleted.toggle() } label: {
                    Text(showingCompleted ? "Hide completed" : "Show \(goal.tasks.count - hiddenCount) completed")
                        .font(.subheadline)
                }
            }

            Button { showingMilestonePrompt = true } label: {
                Label("Add milestone", systemImage: "flag.badge.plus")
                    .font(.subheadline.weight(.semibold))
            }
            .buttonStyle(.bordered)
        }
    }

    private func notesSection(_ goal: Goal) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            TextEditor(text: $notesDraft)
                .frame(minHeight: 220)
                .padding(8)
                .background(AppTheme.surface, in: RoundedRectangle(cornerRadius: 14))
                .overlay {
                    RoundedRectangle(cornerRadius: 14).stroke(AppTheme.line)
                }
            Text("\(notesDraft.count)/10,000 characters")
                .font(.caption).foregroundStyle(AppTheme.tertiaryText)
            Button("Save notes") { store.updateNotes(goalID: goal.id, notes: notesDraft) }
                .buttonStyle(.borderedProminent)
                .disabled(notesDraft == goal.notes)
        }
    }

    private func activitySection(_ goal: Goal) -> some View {
        let items = store.workspace.activities
            .filter { $0.goalId == goal.id }
            .sorted { $0.at > $1.at }
        return VStack(alignment: .leading, spacing: 12) {
            if items.isEmpty {
                Text("No activity recorded yet.").font(.subheadline).foregroundStyle(AppTheme.secondaryText)
            }
            ForEach(items) { item in
                HStack(alignment: .top, spacing: 10) {
                    Circle().fill(accent.opacity(0.5)).frame(width: 8, height: 8).padding(.top, 6)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.message).font(.subheadline).foregroundStyle(AppTheme.primaryText)
                        Text(activityTimeLabel(item.at)).font(.caption).foregroundStyle(AppTheme.tertiaryText)
                    }
                    Spacer()
                }
            }
        }
        .padding(16)
        .momentumCard()
    }
}

struct TaskBranchView: View {
    @EnvironmentObject private var store: MomentumStore

    let goal: Goal
    let task: GoalTask
    let level: Int
    let showingCompleted: Bool
    let addSubtask: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            TaskRow(goalID: goal.id, task: task)
                .padding(.leading, CGFloat(level) * 8)
                .contextMenu {
                    if task.parentTaskId == nil {
                        Button { addSubtask(task.id) } label: {
                            Label("Add subtask", systemImage: "plus")
                        }
                    }
                    Button(role: .destructive) {
                        store.deleteTask(goalID: goal.id, taskID: task.id)
                    } label: {
                        Label("Delete task", systemImage: "trash")
                    }
                }
            ForEach(subtasks(in: goal, for: task.id).filter { showingCompleted || !$0.completed }) { sub in
                TaskBranchView(
                    goal: goal, task: sub, level: level + 1,
                    showingCompleted: showingCompleted, addSubtask: addSubtask
                )
            }
        }
    }
}
