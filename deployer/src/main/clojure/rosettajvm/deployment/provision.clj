(ns rosettajvm.deployment.provision
  (:require [environ.core :as environ]
            [pallet.api :as api]
            [pallet.configure :as configure]
            [clojure.tools.logging :as logger])
  (:use [pallet.crate.automated-admin-user :only [with-automated-admin-user]]
        [rosettajvm.deployment.crate.elastic-ip :only [with-ip-reassignment]]
        [rosettajvm.deployment.crate.openjdk-7-jre :only [with-openjdk-7-jre]]
        [rosettajvm.deployment.crate.rosetta-jvm :only [with-rosetta-jvm]]
        [rosettajvm.deployment.services :only [compute-service]])
  (:gen-class :name "rosettajvm.deployment.Provisioner"))

(defn ubuntu-node [provider]
  (case provider
    :aws (api/node-spec
           :image {:os-family :ubuntu
                   :image-id "us-east-1/ami-77147f1e"}
           :hardware {:hardware-id "t1.micro"}
           :network {:inbound-ports [22 8000]})
    :vmfest (api/node-spec
              :image {:os-family :ubuntu
                      :image-id "ubuntu-12.04"}
              :network {:inbound-ports [22 8000]})))

(defn application-nodes
  ([provider] (application-nodes provider ""))
  ([provider commit-sha]
    (api/group-spec "rosetta-jvm"
      :extends [with-automated-admin-user
                with-openjdk-7-jre
                (with-rosetta-jvm commit-sha)
                (with-ip-reassignment "rosetta-jvm")]
      :node-spec (ubuntu-node provider))))

(defn bring-node-up-on [provider commit-sha]
  (api/converge
    (merge (application-nodes provider commit-sha) {:count 1})
    :compute (compute-service)
    :phase [:configure :install :finalise]
    :async true))

(defn bring-node-down-on [provider]
  (api/converge
    (merge (application-nodes provider) {:count 0})
    :compute (compute-service)
    :phase [:configure :install :finalise]
    :async true))

(defn -main [& args]
  (let [action (first args)
        provider (keyword (first (rest args)))
        commit-sha (first (rest (rest args)))]
    (case action
      "up" (deref (bring-node-up-on provider commit-sha))
      "down" (deref (bring-node-down-on provider)))))