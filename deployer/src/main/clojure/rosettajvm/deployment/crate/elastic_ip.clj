(ns rosettajvm.deployment.crate.elastic-ip
  (:require [environ.core :as environ]
            [pallet.api :as api]
            [pallet.node :as node]
            [pallet.crate :as crate]
            [org.jclouds.ec2.elastic-ip2 :as elastic-ip2]
            [clojure.tools.logging :as logging]))

(crate/defplan reassign-elastic-ip [group-id]
  (let [compute-service (crate/compute-service)
        elastic-ips (elastic-ip2/addresses compute-service)
        elastic-ip (key (first (filter #(not (contains? (val %) :node-id )) elastic-ips)))
        node (crate/target-node)
        instance-id (.getProviderId node)]
    (logging/infof "Available elastic IPs: %s" elastic-ips)
    (logging/infof "Associating elastic IP: %s to instance: %s" elastic-ip instance-id)
    (elastic-ip2/associate compute-service node elastic-ip instance-id)))

(defn with-ip-reassignment [group-id]
  (api/server-spec
    :phases {:finalise (api/plan-fn
                         (reassign-elastic-ip [group-id]))}))