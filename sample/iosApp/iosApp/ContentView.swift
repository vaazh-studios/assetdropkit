import AssetDropKit
import ComposeApp
import SwiftUI

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Launch with -appleHosted to fetch the Apple-hosted pack on iOS 26 (served by
        // `xcrun ba-serve` or TestFlight); otherwise the ODR tag, which works everywhere.
        let appleHosted = ProcessInfo.processInfo.arguments.contains("-appleHosted")
        let adapter = AssetDropAdapter(packs: AppleAssetPacks(onDemandResourcesOnly: !appleHosted))
        return MainViewControllerKt.MainViewController(store: adapter)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View { ComposeView().ignoresSafeArea() }
}
