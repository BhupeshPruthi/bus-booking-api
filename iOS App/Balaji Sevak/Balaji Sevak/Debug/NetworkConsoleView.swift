#if DEBUG
import PulseUI
import SwiftUI

struct NetworkConsoleView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ConsoleView(mode: .network)
                .navigationTitle("Network Inspector")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Done", action: dismiss.callAsFunction)
                    }
                }
        }
    }
}
#endif
