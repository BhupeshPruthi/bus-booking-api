import SwiftUI
import WidgetKit

@main
struct ChalisaWidgetBundle: WidgetBundle {
    var body: some Widget {
        ChalisaLiveActivity()
        if #available(iOS 18.0, *) {
            ChalisaCountControl()
        }
    }
}
