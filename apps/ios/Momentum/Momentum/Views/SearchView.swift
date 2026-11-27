import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var store: MomentumStore
    @EnvironmentObject private var nav: NavigationState
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""

    private struct SearchHit: Identifiable {
        let id = UUID()
        let title: String
        let subtitle: String
        let systemImage: String
        let goalID: String?
    }

    private var hits: [SearchHit] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard needle.count >= 2 else { return [] }
        var results: [SearchHit] = []

        for goal in store.workspace.goals where !goal.isTrashed {
            func matches(_ text: String?) -> Bool {
                (text ?? "").lowercased().contains(needle)
            }
            if matches(goal.title) || matches(goal.description) || matches(goal.notes) {
                results.append(SearchHit(
                    title: goal.title, subtitle: goal.description.isEmpty ? "Goal" : goal.description,
                    systemImage: "target", goalID: goal.id
                ))
            }
            for task in goal.tasks where matches(task.title) || matches(task.notes) {
                results.append(SearchHit(
                    title: task.title, subtitle: goal.title,
                    systemImage: task.parentTaskId == nil ? "checklist" : "arrow.turn.down.right",
                    goalID: goal.id
                ))
            }
            for milestone in goal.milestones where matches(milestone.title) {
                results.append(SearchHit(
                    title: milestone.title, subtitle: goal.title,
                    systemImage: "flag.fill", goalID: goal.id
                ))
            }
        }
        for category in store.liveCategories where category.name.lowercased().contains(needle) {
            results.append(SearchHit(
                title: category.name, subtitle: "Category",
                systemImage: category.icon.symbolName, goalID: nil
            ))
        }
        return results
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    TextField("Goals, tasks, notes, categories", text: $query)
                        .textInputAutocapitalization(.never)
                }
                if query.count >= 2 {
                    Section("Results") {
                        if hits.isEmpty {
                            Text("No matches").foregroundStyle(AppTheme.secondaryText)
                        }
                        ForEach(hits) { hit in
                            Button {
                                if let goalID = hit.goalID {
                                    dismiss()
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                        nav.openGoal(goalID)
                                    }
                                }
                            } label: {
                                HStack {
                                    Image(systemName: hit.systemImage)
                                        .foregroundStyle(Color(hex: store.settings.accent))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(hit.title).foregroundStyle(AppTheme.primaryText)
                                        Text(hit.subtitle)
                                            .font(.caption)
                                            .foregroundStyle(AppTheme.secondaryText)
                                            .lineLimit(1)
                                    }
                                }
                            }
                            .disabled(hit.goalID == nil)
                        }
                    }
                }
            }
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
