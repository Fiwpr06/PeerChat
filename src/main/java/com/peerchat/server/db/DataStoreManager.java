package com.peerchat.server.db;

import java.util.logging.Logger;

// Quản trị viên điều phối tầng lưu trữ CSDL của Server
// Tự động kiểm tra MySQL và kích hoạt cơ chế Graceful Fallback sang In-Memory nếu không có CSDL
public class DataStoreManager {
    private static final Logger LOGGER = Logger.getLogger(DataStoreManager.class.getName());

    private static DataStoreManager instance;

    private final ChatDataStore activeStore;
    private final AsyncDbWorker asyncDbWorker;
    private final boolean mySqlActive;

    private DataStoreManager() {
        this.asyncDbWorker = new AsyncDbWorker();
        this.asyncDbWorker.start();

        // Bước 1: Thử khởi tạo MySQL DataStore
        MySqlDataStore mySqlStore = new MySqlDataStore();
        boolean success = false;
        try {
            success = mySqlStore.init();
        } catch (Throwable t) {
            LOGGER.warning("[DATA_STORE] Ngoại lệ khi thử kết nối MySQL: " + t.getMessage());
            success = false;
        }

        if (success) {
            this.activeStore = mySqlStore;
            this.mySqlActive = true;
            LOGGER.info("==========================================================");
            LOGGER.info("  [CSDL PEERCHAT] ĐÃ KẾT NỐI THÀNH CÔNG VỚI CSDL MYSQL!");
            LOGGER.info("  Bảng mã: utf8mb4 (Hỗ trợ 100% tiếng Việt & Emoji biểu cảm)");
            LOGGER.info("==========================================================");
        } else {
            // Bước 2: Kích hoạt Graceful Fallback sang InMemoryDataStore
            this.activeStore = new InMemoryDataStore();
            this.activeStore.init();
            this.mySqlActive = false;
            LOGGER.warning("==========================================================");
            LOGGER.warning("  [CẢNH BÁO] Không tìm thấy hoặc không thể kết nối MySQL!");
            LOGGER.warning("  -> TỰ ĐỘNG CHUYỂN SANG CHẾ ĐỘ IN-MEMORY AN TOÀN.");
            LOGGER.warning("  Hệ thống vẫn hoạt động 100% bình thường trên RAM.");
            LOGGER.warning("==========================================================");
        }
    }

    public static synchronized DataStoreManager getInstance() {
        if (instance == null) {
            instance = new DataStoreManager();
        }
        return instance;
    }

    // Cho phép reset instance cho các bài test độc lập
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    public ChatDataStore getStore() {
        return activeStore;
    }

    public AsyncDbWorker getAsyncDbWorker() {
        return asyncDbWorker;
    }

    public boolean isMySqlActive() {
        return mySqlActive;
    }

    public String getStoreType() {
        return activeStore.getStoreType();
    }

    public void close() {
        if (asyncDbWorker != null) {
            asyncDbWorker.stop();
        }
        if (activeStore != null) {
            activeStore.close();
        }
    }
}
