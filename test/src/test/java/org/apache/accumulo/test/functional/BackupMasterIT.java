/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.test.functional;

import java.util.Collections;
import java.util.List;

import org.apache.accumulo.fate.util.UtilWaitThread;
import org.apache.accumulo.fate.zookeeper.ZooReaderWriter;
import org.apache.accumulo.fate.zookeeper.ZooUtil.NodeMissingPolicy;
import org.apache.accumulo.master.Master;
import org.junit.Test;

public class BackupMasterIT extends ConfigurableMacIT {

  @Test(timeout= 10 * 1000)
  public void test() throws Exception {
    // wait for master
    UtilWaitThread.sleep(1000);
    // create a backup
    Process backup = exec(Master.class);
    try {
      ZooReaderWriter writer = new ZooReaderWriter(cluster.getZooKeepers(), 30*1000, "digest", "accumulo:DONTTELL".getBytes());
      String root = "/accumulo/" + getConnector().getInstance().getInstanceID();
      List<String> children = Collections.emptyList();
      // wait for 2 lock entries
      do {
        UtilWaitThread.sleep(100);
        children = writer.getChildren(root + "/masters/lock");
      } while (children.size() != 2);
      Collections.sort(children);
      // wait for the backup master to learn to be the backup
      UtilWaitThread.sleep(1000);
      // generate a false zookeeper event
      String lockPath = root + "/masters/lock/" + children.get(0);
      byte data[] = writer.getData(lockPath, null);
      writer.getZooKeeper().setData(lockPath, data, -1);
      // let it propagate
      UtilWaitThread.sleep(500);
      // kill the master by removing its lock
      writer.recursiveDelete(lockPath, NodeMissingPolicy.FAIL);
      // ensure the backup becomes the master
      getConnector().tableOperations().create(getTableNames(1)[0]);
    } finally {
      backup.destroy();
    }
  }

}
