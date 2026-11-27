import SwiftUI

enum GoalStatusFilter: String, CaseIterable, Identifiable {
    case active, completed, all
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}

enum GoalSort: String, CaseIterable, Identifiable {
    case priority, deadline, progress, recent, title
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}

struct GoalsView: View {
    @EnvironmentObject private var store: MomentumStore
    @EnvironmentObject private var nav: NavigationState

    @State private var query = ""
    @State private var status: GoalStatusFilter = .active
    @State private var priorityFilter: Priority?
    @State private var categoryID: String?
    @State private var sort: GoalSort = .priority

    private var visibleGoals: [Goal] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        var items = store.workspace.goals.filter { goal in
            if goal.isTrashed { return false }
            switch status {
            case .active: if !goal.isActive { return false }
            case .completed: if goal.status != .completed || goal.isArchived { return false }
            case .all: break
            }
            if let priorityFilter, goal.priority != priorityFilter { return false }
            if let categoryID, goal.categoryId != categoryID { return false }
            if !needle.isEmpty {
                let haystack = ([goal.title, goal.description, goal.notes] + goal.tasks.map(\.title) + goal.milestones.map(\.title))
                    .joined(separator: " ").lowercased()
                if !haystack.contains(needle) { return false }
            }
            return true
        }
        switch sort {
        case .priority: items.sort { $0.priority.rank < $1.priority.rank }
        case .deadline: items.sort { ($0.deadline ?? "9999") < ($1.deadline ?? "9999") }
        case .progress: items.sort { goalPercent($0) > goalPercent($1) }
        case .recent: items.sort { $0.updatedAt > $1.updatedAt }
        case .title: items.sort { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        }
        return items
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Goals").font(.largeTitle.bold()).foregroundStyle(AppTheme.primaryText)
                    Text("\(store.activeGoals.count) active · \(store.workspace.goals.filter { $0.status == .completed && !$0.isTrashed }.count) completed")
                        .font(.subheadline).foregroundStyle(AppTheme.secondaryText)
                }
                Picker("Status", selection: $status) {
                    ForEach(GoalStatusFilter.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)

                filterBar
                if visibleGoals.isEmpty {
                    EmptyState(
                        systemImage: "target",
                        title: "No goals here yet",
                        message: "Try another filter, or start a new goal.",
                        actionTitle: "New goal"
                    ) { nav.newGoal() }
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 14)], spacing: 14) {
                        ForEach(visibleGoals) { goal in GoalCard(goal: goal) }
                    }
                }
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("Goals")
        .searchable(text: $query, prompt: "Search goals, tasks, notes")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { nav.showSearch = true } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel("Search")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("Sort", selection: $sort) {
                        ForEach(GoalSort.allCases) { Text($0.label).tag($0) }
                    }
                    Picker("Priority", selection: $priorityFilter) {
                        Text("Any priority").tag(nil as Priority?)
                        ForEach(Priority.allCases) { Text($0.label).tag($0 as Priority?) }
                    }
                    Picker("Category", selection: $categoryID) {
                        Text("Any category").tag(nil as String?)
                        ForEach(store.liveCategories) { Text($0.name).tag($0.id as String?) }
                    }
                } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                }
                .accessibilityLabel("Sort and filter")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { nav.newGoal() } label: { Image(systemName: "plus.circle.fill") }
                    .accessibilityLabel("New goal")
            }
        }
    }

    private var filterBar: some View {
        HStack(spacing: 8) {
            if let priorityFilter {
                Button { self.priorityFilter = nil } label: {
                    Label(priorityFilter.label, systemImage: "xmark.circle.fill")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
            }
            if let categoryID, let category = store.category(id: categoryID) {
                Button { self.categoryID = nil } label: {
                    Label(category.name, systemImage: "xmark.circle.fill")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
            }
            Spacer()
            Button { nav.showArchive = true } label: {
                Label("Archive", systemImage: "archivebox")
                    .font(.caption)
            }
            Button { nav.showTrash = true } label: {
                Label("Trash", systemImage: "trash")
                    .font(.caption)
            }
        }
        .foregroundStyle(AppTheme.secondaryText)
    }
}

struct ArchiveView: View {
    @EnvironmentObject private var store: MomentumStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            if store.archivedGoals.isEmpty {
                EmptyState(systemImage: "archivebox", title: "Archive is empty", message: "Archived goals will appear here.")
                    .listRowBackground(Color.clear)
            }
            ForEach(store.archivedGoals) { goal in
                VStack(alignment: .leading, spacing: 8) {
                    Text(goal.title).font(.headline)
                    Text("\(goalPercent(goal))% complete")
                        .font(.caption).foregroundStyle(AppTheme.secondaryText)
                    HStack {
                        Button("Restore") { store.archiveGoal(goal.id, archived: false) }
                        Button("Move to trash", role: .destructive) { store.trashGoal(goal.id) }
                    }
                    .font(.caption)
                }
                .padding(.vertical, 4)
            }
        }
        .navigationTitle("Archive")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
        }
    }
}

struct TrashView: View {
    @EnvironmentObject private var store: MomentumStore
    @Environment(\.dismiss) private var dismiss
    @State private var showingEmptyConfirm = false

    private func daysLeft(_ goal: Goal) -> Int {
        guard let deleted = parseTimestamp(goal.deletedAt),
              let expiry = Calendar.current.date(byAdding: .day, value: 30, to: deleted) else { return 0 }
        return max(0, calendarDays(from: Date(), to: expiry))
    }

    var body: some View {
        List {
            Section {
                Text("Deleted goals stay here for 30 days, then they are removed automatically.")
                    .font(.caption).foregroundStyle(AppTheme.secondaryText)
            }
            if store.trashedGoals.isEmpty {
                EmptyState(systemImage: "trash", title: "Trash is empty", message: "Nothing to recover.")
                    .listRowBackground(Color.clear)
            }
            ForEach(store.trashedGoals) { goal in
                VStack(alignment: .leading, spacing: 8) {
                    Text(goal.title).font(.headline)
                    Text("\(daysLeft(goal)) days left")
                        .font(.caption).foregroundStyle(AppTheme.secondaryText)
                    HStack {
                        Button("Restore") { store.restoreGoal(goal.id) }
                        Button("Delete forever", role: .destructive) { store.deleteGoalForever(goal.id) }
                    }
                    .font(.caption)
                }
                .padding(.vertical, 4)
            }
            if !store.trashedGoals.isEmpty {
                Button("Empty trash", role: .destructive) { showingEmptyConfirm = true }
            }
        }
        .navigationTitle("Recently deleted")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
        }
        .alert("Empty trash?", isPresented: $showingEmptyConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Delete all", role: .destructive) { store.emptyTrash() }
        }
    }
}
