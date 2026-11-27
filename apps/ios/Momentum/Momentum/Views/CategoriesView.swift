import SwiftUI

private struct CategoryTarget: Identifiable {
    let id = UUID()
    let category: Category?
}

struct CategoriesView: View {
    @EnvironmentObject private var store: MomentumStore
    @State private var editor: CategoryTarget?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Categories").font(.largeTitle.bold()).foregroundStyle(AppTheme.primaryText)
                    Text("Group goals by the part of life they belong to.")
                        .font(.subheadline).foregroundStyle(AppTheme.secondaryText)
                }

                if store.liveCategories.isEmpty {
                    EmptyState(
                        systemImage: "folder", title: "No categories yet",
                        message: "Create one to keep goals organised."
                    ) { editor = CategoryTarget(category: nil) }
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 240), spacing: 14)], spacing: 14) {
                        ForEach(store.liveCategories) { category in
                            categoryCard(category)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("Categories")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { editor = CategoryTarget(category: nil) } label: {
                    Image(systemName: "plus.circle.fill")
                }
                .accessibilityLabel("New category")
            }
        }
        .sheet(item: $editor) { target in
            CategoryEditorView(category: target.category)
        }
    }

    private func categoryCard(_ category: Category) -> some View {
        let goals = store.workspace.goals.filter { $0.categoryId == category.id && !$0.isTrashed }
        let active = goals.filter(\.isActive).count
        let average = goals.isEmpty ? 0 : goals.reduce(0) { $0 + goalPercent($1) } / goals.count
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: category.icon.symbolName)
                    .font(.title3)
                    .foregroundStyle(Color(hex: category.color))
                    .frame(width: 38, height: 38)
                    .background(Color(hex: category.color).opacity(0.14), in: RoundedRectangle(cornerRadius: 11))
                Spacer()
                Button { editor = CategoryTarget(category: category) } label: {
                    Image(systemName: "pencil").foregroundStyle(AppTheme.tertiaryText)
                }
                .buttonStyle(.plain)
            }
            Text(category.name)
                .font(.headline).foregroundStyle(AppTheme.primaryText)
            HStack {
                Text("\(active) active · \(goals.count) total")
                    .font(.caption).foregroundStyle(AppTheme.secondaryText)
                Spacer()
                Text("\(average)% avg")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color(hex: category.color))
            }
            ProgressView(value: Double(average), total: 100)
                .tint(Color(hex: category.color))
        }
        .padding(16)
        .momentumCard()
        .contextMenu {
            Button(role: .destructive) { store.deleteCategory(category.id) } label: {
                Label("Delete category", systemImage: "trash")
            }
        }
    }
}

struct CategoryEditorView: View {
    @EnvironmentObject private var store: MomentumStore
    @Environment(\.dismiss) private var dismiss

    let category: Category?

    @State private var name = ""
    @State private var color = AccentPreset.all[0].hex
    @State private var icon: CategoryIcon = .target

    private var isValid: Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.count <= 60
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Details") {
                    TextField("Category name", text: $name)
                    Picker("Icon", selection: $icon) {
                        ForEach(CategoryIcon.allCases) { Text($0.label).tag($0) }
                    }
                }
                Section("Color") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8), spacing: 10) {
                        ForEach(AccentPreset.all, id: \.hex) { preset in
                            Button {
                                color = preset.hex
                            } label: {
                                Circle()
                                    .fill(Color(hex: preset.hex))
                                    .frame(width: 26, height: 26)
                                    .overlay {
                                        if color == preset.hex {
                                            Image(systemName: "checkmark")
                                                .font(.caption.weight(.bold))
                                                .foregroundStyle(AppTheme.contrastText(for: preset.hex))
                                        }
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle(category == nil ? "New category" : "Edit category")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(!isValid)
                }
            }
            .onAppear {
                guard let category else { return }
                name = category.name
                color = category.color
                icon = category.icon
            }
        }
    }

    private func save() {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 60 else { return }
        if let category {
            var updated = category
            updated.name = clean
            updated.color = color
            updated.icon = icon
            store.updateCategory(updated)
        } else {
            store.addCategory(name: clean, color: color, icon: icon)
        }
        dismiss()
    }
}
