import SwiftUI

@main
struct NativeSampleApp: App {
    @State private var isDark = false

    var body: some Scene {
        WindowGroup {
            RootView(isDark: $isDark)
                .preferredColorScheme(isDark ? .dark : .light)
        }
    }
}

enum SampleDestination: Hashable {
    case editorDemo
    case comments
    case customBlocks
}

struct RootView: View {
    @Binding var isDark: Bool
    @State private var path: [SampleDestination] = []

    private var theme: AppTheme { AppTheme.theme(isDark: isDark) }

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
            EditorDemoScreen(isDark: $isDark)
        case .comments:
            CommentsScreen(isDark: $isDark)
        case .customBlocks:
            CustomBlocksScreen(isDark: $isDark)
        }
    }
}
