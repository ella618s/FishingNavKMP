import MapKit

class OfflineTileOverlay: MKTileOverlay {
    override func url(forTilePath path: MKTileOverlayPath) -> URL {
        // 1. 檢查本地是否存在該瓦片檔案 (例如在 Documents/tiles/ 之下)
        let fileManager = FileManager.default
        let tilePath = getLocalPath(path)
        
        if fileManager.fileExists(atPath: tilePath.path) {
            return tilePath // ✅ 回傳本地路徑
        }
        
        // 2. 如果本地沒有，回傳原本的遠端 URL (或進行下載存檔)
        return super.url(forTilePath: path)
    }
    
    private func getLocalPath(_ path: MKTileOverlayPath) -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("tiles/\(path.z)/\(path.x)/\(path.y).png")
    }
}
