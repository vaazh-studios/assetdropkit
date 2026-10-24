import Foundation

/// Where a pack stands on this device, the same names the Kotlin side uses.
public enum PackStatus: Sendable, Equatable {
    case notDownloaded
    /// Queued or paused by the system.
    case pending
    /// `total` is 0 when the system does not say.
    case downloading(bytes: Int64, total: Int64)
    case ready
    case failed(PackError)
}

/// Why a fetch failed.
public enum PackError: Error, Sendable, Equatable {
    case noNetwork
    case insufficientStorage
    case notFound
    case cancelled
    /// Anything else, with the system's description for logs.
    case unknown(String)
}

/// System progress and errors as `PackStatus` and `PackError`.
enum PackStatusMapping {

    static func status(of progress: Progress) -> PackStatus {
        .downloading(bytes: max(progress.completedUnitCount, 0), total: max(progress.totalUnitCount, 0))
    }

    /// NSBundleOnDemandResourceOutOfSpaceError, ExceededMaximumSizeError and InvalidTagError (iOS-only constants).
    private static let odrOutOfSpace = 4992
    private static let odrExceededMaximumSize = 4993
    private static let odrInvalidTag = 4994

    private static let offline: Set<Int> = [
        NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost, NSURLErrorTimedOut,
        NSURLErrorCannotConnectToHost, NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed,
        NSURLErrorDataNotAllowed, NSURLErrorInternationalRoamingOff,
    ]

    static func error(_ error: Error) -> PackError {
        if let mapped = error as? PackError { return mapped }
        if error is CancellationError { return .cancelled }
        if let managed = managedError(error) { return managed }
        let ns = error as NSError
        switch ns.domain {
        case NSURLErrorDomain:
            if ns.code == NSURLErrorCancelled { return .cancelled }
            if offline.contains(ns.code) { return .noNetwork }
        case NSCocoaErrorDomain:
            switch ns.code {
            case NSUserCancelledError: return .cancelled
            case NSFileWriteOutOfSpaceError, odrOutOfSpace, odrExceededMaximumSize: return .insufficientStorage
            case odrInvalidTag, NSFileReadNoSuchFileError, NSFileNoSuchFileError: return .notFound
            default: break
            }
        default: break
        }
        return .unknown("\(ns.domain) \(ns.code): \(ns.localizedDescription)")
    }

    private static func managedError(_ error: Error) -> PackError? {
        #if canImport(BackgroundAssets) && !os(watchOS)
        if #available(iOS 26, macOS 26, tvOS 26, visionOS 26, *), error is ManagedBackgroundAssetsError {
            return .notFound
        }
        #endif
        return nil
    }
}

#if canImport(BackgroundAssets) && !os(watchOS)
import BackgroundAssets
#endif
