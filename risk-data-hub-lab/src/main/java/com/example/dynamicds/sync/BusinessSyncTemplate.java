package com.example.dynamicds.sync;

import com.example.dynamicds.sync.SyncSupport.BusinessSyncResult;
import com.example.dynamicds.sync.SyncSupport.SyncProgressListener;

public interface BusinessSyncTemplate {

    String businessCode();

    BusinessSyncResult execute(BusinessSyncContext context, SyncProgressListener progressListener) throws Exception;
}
