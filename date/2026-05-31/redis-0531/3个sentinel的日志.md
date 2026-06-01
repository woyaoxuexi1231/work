sentinel-1日志：
1:X 01 Jun 2026 15:17:57.208 # +sdown master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.216 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.216 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:17:57.216 # +new-epoch 2
1:X 01 Jun 2026 15:17:57.217 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.217 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:17:57.217 # +vote-for-leader 4a6b4aad9df665359c326d7867e195900ce7779c 2
1:X 01 Jun 2026 15:17:57.310 # +odown master mymaster 172.25.0.4 6379 #quorum 3/2
1:X 01 Jun 2026 15:17:57.310 * Next failover delay: I will not start a failover before Mon Jun  1 15:18:17 2026
1:X 01 Jun 2026 15:17:57.784 # +config-update-from sentinel 4a6b4aad9df665359c326d7867e195900ce7779c 172.25.0.7 26379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.784 # +switch-master mymaster 172.25.0.4 6379 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 172.25.0.1:6379 172.25.0.1 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 192.168.3.100:6379 192.168.3.100 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 172.25.0.2:6379 172.25.0.2 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.786 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.786 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:18:02.855 # +sdown slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:19:07.342 # -sdown slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379



1:X 01 Jun 2026 15:19:23.415 * +fix-slave-config slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379




sentinel-2日志：
1:X 01 Jun 2026 15:17:57.088 # +sdown master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.216 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.216 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:17:57.216 # +new-epoch 2
1:X 01 Jun 2026 15:17:57.217 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.217 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:17:57.217 # +vote-for-leader 4a6b4aad9df665359c326d7867e195900ce7779c 2
1:X 01 Jun 2026 15:17:57.784 # +config-update-from sentinel 4a6b4aad9df665359c326d7867e195900ce7779c 172.25.0.7 26379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.784 # +switch-master mymaster 172.25.0.4 6379 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 172.25.0.2:6379 172.25.0.2 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 192.168.3.100:6379 192.168.3.100 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 172.25.0.1:6379 172.25.0.1 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.785 * +slave slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:17:57.786 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.786 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:18:02.836 # +sdown slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:19:07.370 # -sdown slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379



sentinel-3日志：
1:X 01 Jun 2026 15:17:57.150 # +sdown master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.213 # +odown master mymaster 172.25.0.4 6379 #quorum 2/2
1:X 01 Jun 2026 15:17:57.213 # +new-epoch 2
1:X 01 Jun 2026 15:17:57.213 # +try-failover master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.214 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.214 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:17:57.214 # +vote-for-leader 4a6b4aad9df665359c326d7867e195900ce7779c 2
1:X 01 Jun 2026 15:17:57.217 * a10da372ef679537a21e7baf7f93cdd9d0103144 voted for 4a6b4aad9df665359c326d7867e195900ce7779c 2
1:X 01 Jun 2026 15:17:57.217 * e3d82311ed92d423979bdccb6bf5cc5a18cb84df voted for 4a6b4aad9df665359c326d7867e195900ce7779c 2
1:X 01 Jun 2026 15:17:57.277 # +elected-leader master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.277 # +failover-state-select-slave master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.330 # +selected-slave slave 172.25.0.3:6379 172.25.0.3 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.330 * +failover-state-send-slaveof-noone slave 172.25.0.3:6379 172.25.0.3 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.431 * +failover-state-wait-promotion slave 172.25.0.3:6379 172.25.0.3 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.708 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:17:57.709 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:17:57.709 # +promoted-slave slave 172.25.0.3:6379 172.25.0.3 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.709 # +failover-state-reconf-slaves master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:57.784 * +slave-reconf-sent slave 172.25.0.1:6379 172.25.0.1 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:58.238 * +slave-reconf-inprog slave 172.25.0.1:6379 172.25.0.1 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:58.238 * +slave-reconf-done slave 172.25.0.1:6379 172.25.0.1 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:58.297 # -odown master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:58.297 * +slave-reconf-sent slave 172.25.0.2:6379 172.25.0.2 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:59.239 * +slave-reconf-inprog slave 172.25.0.2:6379 172.25.0.2 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:59.239 * +slave-reconf-done slave 172.25.0.2:6379 172.25.0.2 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:17:59.323 * +slave-reconf-sent slave 192.168.3.100:6379 192.168.3.100 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:18:00.271 * +slave-reconf-inprog slave 192.168.3.100:6379 192.168.3.100 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:18:00.271 * +slave-reconf-done slave 192.168.3.100:6379 192.168.3.100 6379 @ mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:18:00.361 # +failover-end master mymaster 172.25.0.4 6379
1:X 01 Jun 2026 15:18:00.361 # +switch-master mymaster 172.25.0.4 6379 172.25.0.3 6379
1:X 01 Jun 2026 15:18:00.362 * +slave slave 172.25.0.1:6379 172.25.0.1 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:18:00.362 * +slave slave 172.25.0.2:6379 172.25.0.2 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:18:00.362 * +slave slave 192.168.3.100:6379 192.168.3.100 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:18:00.362 * +slave slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:18:00.363 # Could not rename tmp config file (Device or resource busy)
1:X 01 Jun 2026 15:18:00.363 # WARNING: Sentinel was not able to save the new configuration on disk!!!: Device or resource busy
1:X 01 Jun 2026 15:18:05.423 # +sdown slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379
1:X 01 Jun 2026 15:19:07.317 # -sdown slave 172.25.0.4:6379 172.25.0.4 6379 @ mymaster 172.25.0.3 6379
