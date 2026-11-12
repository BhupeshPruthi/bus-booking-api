import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

private let saffron = Color(red: 252 / 255, green: 121 / 255, blue: 13 / 255)

/// The Lock Screen and Dynamic Island counter: +1, Undo and Stop, without unlocking the phone.
struct ChalisaLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ChalisaActivityAttributes.self) { context in
            LockScreenCounter(state: context.state)
                .activityBackgroundTint(Color(.systemBackground).opacity(0.85))
                .activitySystemActionForegroundColor(saffron)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("Chalisa", systemImage: "flame.fill").font(.caption.weight(.semibold)).foregroundStyle(saffron)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("Today \(context.state.today)").font(.caption.weight(.semibold)).monospacedDigit()
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(context.state.detail).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        CounterButtons(state: context.state)
                    }
                }
            } compactLeading: {
                Image(systemName: "flame.fill").foregroundStyle(saffron)
            } compactTrailing: {
                Text("\(context.state.today)").monospacedDigit()
            } minimal: {
                Image(systemName: "flame.fill").foregroundStyle(saffron)
            }
        }
    }
}

private struct LockScreenCounter: View {
    let state: ChalisaActivityAttributes.ContentState
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "flame.fill").font(.title3).foregroundStyle(saffron)
                VStack(alignment: .leading, spacing: 2) {
                    Text(state.headline).font(.headline).monospacedDigit()
                    Text(state.detail).font(.caption).foregroundStyle(.secondary).lineLimit(3)
                }
                Spacer(minLength: 0)
            }
            CounterButtons(state: state)
        }
        .padding(14)
    }
}

private struct CounterButtons: View {
    let state: ChalisaActivityAttributes.ContentState
    var body: some View {
        HStack(spacing: 8) {
            if state.showsCounter {
                Button(intent: ChalisaCountRoundIntent()) {
                    Text("+1 Round").font(.subheadline.weight(.bold)).frame(maxWidth: .infinity)
                }
                .tint(saffron)
                if let until = state.undoUntil, until > .now {
                    Button(intent: ChalisaUndoRoundIntent()) { Text("Undo").font(.subheadline) }
                        .tint(.gray)
                }
            }
            Button(intent: ChalisaStopCounterIntent()) { Text("Stop").font(.subheadline) }
                .tint(.gray)
        }
        .buttonStyle(.borderedProminent)
    }
}

/// Control Center button "Chalisa +1" (Android's Quick Settings tile). Works from the Lock Screen too.
@available(iOS 18.0, *)
struct ChalisaCountControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "com.balajisevak.chalisa.count") {
            ControlWidgetButton(action: ChalisaCountRoundIntent()) {
                Label("Chalisa +1", systemImage: "flame.fill")
            }
        }
        .displayName("Chalisa +1")
        .description("Count a Hanuman Chalisa round.")
    }
}
