import SwiftUI

@main
struct NativeSampleApp: App {
    @State private var selectedTheme = SampleThemeFamily.violet

    var body: some Scene {
        WindowGroup {
            RootView(selectedTheme: $selectedTheme)
        }
    }
}

enum SampleDestination: Hashable {
    case editorDemo
    case comments
    case customBlocks
}

struct RootView: View {
    @Binding var selectedTheme: SampleThemeFamily
    @Environment(\.colorScheme) private var colorScheme
    @State private var path: [SampleDestination] = []

    private var isDark: Bool { colorScheme == .dark }
    private var theme: AppTheme { selectedTheme.appTheme(isDark: isDark) }

    var body: some View {
        NavigationStack(path: $path) {
            LandingView(theme: theme) { destination in
                path.append(destination)
            }
            .navigationDestination(for: SampleDestination.self) { destination in
                destinationView(for: destination)
                    .navigationBarBackButtonHidden(true)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
    }

    @ViewBuilder
    private func destinationView(for destination: SampleDestination) -> some View {
        switch destination {
        case .editorDemo:
            EditorDemoScreen(selectedTheme: $selectedTheme, initialIsDark: isDark)
        case .comments:
            CommentsScreen(selectedTheme: $selectedTheme, initialIsDark: isDark)
        case .customBlocks:
            CustomBlocksScreen(selectedTheme: $selectedTheme, initialIsDark: isDark)
        }
    }
}
