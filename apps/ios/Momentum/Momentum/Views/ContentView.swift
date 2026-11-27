import SwiftUI

enum AppTab: Hashable {
    case overview, goals, planner, categories, settings
}

final class NavigationState: ObservableObject {
    @Published var tab: AppTab = .overview
    @Published var path: [String] = []
    @Published var editor: GoalEditorTarget?
    @Published var showArchive = false
    @Published var showTrash = false
    @Published var showSearch = false

    func openGoal(_ id: String) {
        tab = .goals
        if path.count == 1 && path.first == id { return }
        path = [id]
    }

    func newGoal() { editor = .new }
    func editGoal(_ goal: Goal) { editor = .edit(goal) }

    func resetPath() { path.removeAll() }
}

struct ContentView: View {
    @EnvironmentObject private var store: MomentumStore
    @StateObject private var nav = NavigationState()

    private var onboardingShown: Binding<Bool> {
        Binding(
            get: { !store.settings.onboardingComplete },
            set: { shown in
                if !shown { store.updateSettings { $0.onboardingComplete = true } }
            }
        )
    }

    var body: some View {
        NavigationStack(path: $nav.path) {
            TabView(selection: $nav.tab) {
                DashboardView()
                    .tabItem { Label("Overview", systemImage: "square.grid.2x2.fill") }
                    .tag(AppTab.overview)
                GoalsView()
                    .tabItem { Label("Goals", systemImage: "target") }
                    .tag(AppTab.goals)
                PlannerView()
                    .tabItem { Label("Planner", systemImage: "calendar.badge.clock") }
                    .tag(AppTab.planner)
                CategoriesView()
                    .tabItem { Label("Categories", systemImage: "folder.fill") }
                    .tag(AppTab.categories)
                SettingsView()
                    .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                    .tag(AppTab.settings)
            }
            .tint(Color(hex: store.settings.accent))
            .navigationDestination(for: String.self) { goalID in
                GoalDetailView(goalID: goalID)
            }
            .sheet(item: $nav.editor) { target in
                GoalEditorView(target: target)
            }
            .sheet(isPresented: $nav.showArchive) {
                NavigationStack { ArchiveView() }
            }
            .sheet(isPresented: $nav.showTrash) {
                NavigationStack { TrashView() }
            }
            .sheet(isPresented: $nav.showSearch) {
                SearchView()
            }
            .safeAreaInset(edge: .bottom) {
                if let error = store.storageError {
                    Text("Storage error: \(error)")
                        .font(.footnote)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(AppTheme.danger.opacity(0.9), in: Capsule())
                        .padding(.bottom, 4)
                }
            }
        }
        .environmentObject(nav)
        .preferredColorScheme(store.settings.themeMode.colorScheme)
        .fullScreenCover(isPresented: onboardingShown) {
            OnboardingView()
        }
        .onAppear { store.purgeExpiredTrash() }
    }
}

extension ThemeMode {
    var colorScheme: ColorScheme? {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }
}

struct OnboardingView: View {
    @EnvironmentObject private var store: MomentumStore

    @State private var step = 0
    @State private var name = ""
    @State private var theme: ThemeMode = .dark
    @State private var accent: AccentPreset = AccentPreset.all[0]
    @State private var firstGoal = ""

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: ["target", "paintbrush.fill", "sparkles"][step])
                .font(.system(size: 58, weight: .light))
                .foregroundStyle(Color(hex: accent.hex))
            VStack(spacing: 10) {
                Text(["Welcome to MOMENTUM", "Make it yours", "What is the first move?"][step])
                    .font(.title.bold())
                    .foregroundStyle(AppTheme.primaryText)
                Text([
                    "Turn an ambition into a plan, then keep the next move visible. Everything stays on this device.",
                    "Pick a look that keeps you focused. You can change it any time in Settings.",
                    "Start with one goal. Add tasks and milestones whenever you are ready. You can skip this.",
                ][step])
                    .font(.body)
                    .foregroundStyle(AppTheme.secondaryText)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 12)

            Group {
                if step == 1 {
                    VStack(spacing: 14) {
                        TextField("Your name", text: $name)
                            .textFieldStyle(.roundedBorder)
                        Picker("Theme", selection: $theme) {
                            ForEach(ThemeMode.allCases) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 8)], spacing: 8) {
                            ForEach(AccentPreset.all) { preset in
                                Button { accent = preset } label: {
                                    Circle()
                                        .fill(Color(hex: preset.hex))
                                        .frame(width: 26, height: 26)
                                        .overlay {
                                            if accent.id == preset.id {
                                                Image(systemName: "checkmark")
                                                    .font(.caption.weight(.bold))
                                                    .foregroundStyle(AppTheme.contrastText(for: preset.hex))
                                            }
                                        }
                                }
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .buttonStyle(.plain)
                            }
                        }
                    }
                } else if step == 2 {
                    TextField("e.g. Run a 5K", text: $firstGoal)
                        .textFieldStyle(.roundedBorder)
                }
            }
            .padding(.horizontal, 24)

            Spacer()
            HStack(spacing: 12) {
                if step > 0 {
                    Button("Back") { step -= 1 }
                    .buttonStyle(.bordered)
                }
                Button(step == 2 ? "Get started" : "Continue") {
                    if step < 2 {
                        step += 1
                    } else {
                        finish()
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(Color(hex: accent.hex))
                if step == 2 {
                    Button("Skip") { finish() }
                    .buttonStyle(.plain)
                }
            }
            .padding(.bottom, 24)
        }
        .background(AppTheme.background.ignoresSafeArea())
    }

    private func finish() {
        store.updateSettings {
            $0.displayName = String(name.prefix(60))
            $0.themeMode = theme
            $0.accent = accent.hex
            $0.accentSecondary = accent.secondary
            $0.onboardingComplete = true
        }
        let title = firstGoal.trimmingCharacters(in: .whitespacesAndNewlines)
        if !title.isEmpty {
            _ = store.addGoal(GoalDraft(
                title: title, description: "", notes: "", categoryID: nil,
                kind: .oneTime, recurrence: nil, priority: .medium,
                startDate: nil, deadline: nil
            ))
        }
    }
}
