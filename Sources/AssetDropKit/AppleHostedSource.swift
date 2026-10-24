#if canImport(BackgroundAssets) && !os(watchOS)
import BackgroundAssets
import Foundation
import System

/// Apple-hosted Background Assets (iOS 26 and later). Packs are archives built with
/// `xcrun ba-package` and uploaded to App Store Connect; the app's downloader extension
/// (Apple-Hosted, Managed) lets the system fetch them.
@available(iOS 26, macOS 26, tvOS 26, visionOS 26, *)
struct AppleHostedSource: ApplePackSource {

    private var manager: AssetPackManager { .shared }

    func status(_ id: String) async -> PackStatus {
        do {
            let status: AssetPack.Status
            if #available(iOS 26.4, macOS 26.4, tvOS 26.4, visionOS 26.4, *) {
                status = await manager.localStatus(ofAssetPackWithID: id)
            } else {
                status = try await manager.status(ofAssetPackWithID: id)
            }
            return Self.packStatus(status)
        } catch {
            return .failed(PackStatusMapping.error(error))
        }
    }

    static func packStatus(_ status: AssetPack.Status) -> PackStatus {
        if status.contains(.downloaded) { return .ready }
        if status.contains(.downloading) { return .pending }
        return .notDownloaded
    }

    func statusUpdates(_ id: String) -> AsyncStream<PackStatus> {
        let (stream, continuation) = AsyncStream<PackStatus>.makeStream()
        let updates = manager.statusUpdates(forAssetPackWithID: id)
        let task = Task {
            for await update in updates {
                continuation.yield(Self.packStatus(update))
            }
            continuation.finish()
        }
        continuation.onTermination = { _ in task.cancel() }
        return stream
    }

    static func packStatus(_ update: AssetPackManager.DownloadStatusUpdate) -> PackStatus {
        switch update {
        case .began, .paused: return .pending
        case .downloading(_, let progress): return PackStatusMapping.status(of: progress)
        case .finished: return .ready
        case .failed(_, let error): return .failed(PackStatusMapping.error(error))
        @unknown default: return .pending
        }
    }

    func fetch(_ id: String) async throws {
        do {
            let pack = try await manager.assetPack(withID: id)
            if #available(iOS 26.4, macOS 26.4, tvOS 26.4, visionOS 26.4, *) {
                try await manager.ensureLocalAvailability(of: pack, requireLatestVersion: false)
            } else {
                try await manager.ensureLocalAvailability(of: pack)
            }
            try Task.checkCancellation()
        } catch {
            throw Task.isCancelled ? PackError.cancelled : PackStatusMapping.error(error)
        }
    }

    func contents(_ id: String, _ path: String) throws -> Data {
        do {
            return try manager.contents(at: FilePath("\(id)/\(path)"), searchingInAssetPackWithID: id)
        } catch {
            throw PackStatusMapping.error(error)
        }
    }

    func remove(_ id: String) async {
        try? await manager.remove(assetPackWithID: id)
    }
}
#endif
