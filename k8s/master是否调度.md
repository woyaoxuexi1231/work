```shell
# 禁止调度到 Master
kubectl taint nodes k8s-master node-role.kubernetes.io/control-plane:NoSchedule

# 恢复调度
kubectl taint nodes k8s-master node-role.kubernetes.io/control-plane:NoSchedule-
```