import BackgroundAssets
import ExtensionFoundation
import StoreKit

/// Lets the system download the app's Apple-hosted packs; it fetches nothing on its own.
@main
struct DemoDownloader: StoreDownloaderExtension {
    func shouldDownload(_ assetPack: AssetPack) -> Bool { true }
}
