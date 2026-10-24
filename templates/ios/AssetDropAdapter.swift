// AssetDropAdapter.swift: copy into the app target. It connects the AssetDropKit Swift package
// (Apple-hosted packs on iOS 26 and later, On-Demand Resources on iOS 17 and 18) to the Kotlin
// library. A Swift package cannot import the app's Kotlin framework, so this glue lives in the app.
//
// The app's Kotlin framework must re-export the library so Swift sees its names:
//
//     iosTarget.binaries.framework {
//         export("com.vocabloot:assetdropkit:<version>")
//     }
//
// Replace `Shared` below with that framework's module name, then hand an instance to Kotlin:
// `AppleStoreSource(store: AssetDropAdapter())`.

import AssetDropKit
import Foundation
import Shared

final class AssetDropAdapter: NSObject, ApplePackStore {

    private let packs: AppleAssetPacks

    /// Pass `AppleAssetPacks(onDemandResourcesOnly: true)` for Xcode runs on iOS 26, which have no
    /// Apple-hosted pack to fetch.
    init(packs: AppleAssetPacks = AppleAssetPacks()) {
        self.packs = packs
    }

    func fetch(packId: String, callbacks: any ApplePackCallbacks) -> any PackCancel {
        let callbacks = Unchecked(callbacks)
        let packs = packs
        let task = Task {
            let progress = Task {
                for await status in packs.statusUpdates(packId) {
                    if case let .downloading(bytes, total) = status {
                        callbacks.value.onProgress(bytes: bytes, total: total)
                    }
                }
            }
            defer { progress.cancel() }
            do {
                try await packs.fetch(packId)
                callbacks.value.onFinished()
            } catch {
                let failure = Self.failure(error)
                callbacks.value.onFailed(reason: failure.reason, detail: failure.detail)
            }
        }
        return TaskCancel(task)
    }

    func status(packId: String, callbacks: any ApplePackCallbacks) {
        let callbacks = Unchecked(callbacks)
        let packs = packs
        Task {
            switch await packs.status(packId) {
            case .ready:
                callbacks.value.onFinished()
            case .pending, .downloading:
                callbacks.value.onPending()
            case .notDownloaded:
                callbacks.value.onNotDownloaded()
            case .failed(let error):
                let failure = Self.failure(error)
                callbacks.value.onFailed(reason: failure.reason, detail: failure.detail)
            }
        }
    }

    func readBytes(packId: String, path: String) -> Data? {
        try? packs.contents(packId, path)
    }

    func remove(packId: String, callbacks: any ApplePackCallbacks) {
        let callbacks = Unchecked(callbacks)
        let packs = packs
        Task {
            await packs.remove(packId)
            callbacks.value.onFinished()
        }
    }

    private static func failure(_ error: Error) -> (reason: PackFailure, detail: String?) {
        switch error as? PackError {
        case .noNetwork: return (.noNetwork, nil)
        case .insufficientStorage: return (.insufficientStorage, nil)
        case .notFound: return (.notFound, nil)
        case .cancelled: return (.cancelled, nil)
        case .unknown(let detail): return (.unknown, detail)
        case nil: return (.unknown, String(describing: error))
        }
    }
}

/// Kotlin objects are not Sendable; the Kotlin side makes its callbacks safe to call from any thread.
private struct Unchecked<Value>: @unchecked Sendable {
    let value: Value
    init(_ value: Value) { self.value = value }
}

private final class TaskCancel: NSObject, PackCancel {
    private let task: Task<Void, Never>
    init(_ task: Task<Void, Never>) { self.task = task }
    func cancel() { task.cancel() }
}
