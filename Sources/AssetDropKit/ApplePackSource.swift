import Foundation

/// One Apple delivery system: Apple-hosted Background Assets or On-Demand Resources.
/// Paths are inside the pack; every file lives under `<packId>/`.
protocol ApplePackSource: Sendable {
    func status(_ id: String) async -> PackStatus
    func statusUpdates(_ id: String) -> AsyncStream<PackStatus>
    /// Downloads the pack and keeps it on the device; throws `PackError`.
    func fetch(_ id: String) async throws
    func contents(_ id: String, _ path: String) throws -> Data
    func remove(_ id: String) async
}
