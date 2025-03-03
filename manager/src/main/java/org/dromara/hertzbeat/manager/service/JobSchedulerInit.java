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

package org.dromara.hertzbeat.manager.service;

import org.dromara.hertzbeat.collector.dispatch.entrance.internal.CollectJobService;
import org.dromara.hertzbeat.common.entity.job.Configmap;
import org.dromara.hertzbeat.common.entity.job.Job;
import org.dromara.hertzbeat.common.entity.manager.Monitor;
import org.dromara.hertzbeat.common.entity.manager.Param;
import org.dromara.hertzbeat.common.entity.manager.ParamDefine;
import org.dromara.hertzbeat.common.util.JsonUtil;
import org.dromara.hertzbeat.manager.dao.MonitorDao;
import org.dromara.hertzbeat.manager.dao.ParamDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 采集任务调度初始化
 * @author tom
 *
 */
@Service
@Order(value = 2)
@Slf4j
public class JobSchedulerInit implements CommandLineRunner {

    @Autowired
    private AppService appService;

    @Autowired
    private CollectJobService collectJobService;

    @Autowired
    private MonitorDao monitorDao;

    @Autowired
    private ParamDao paramDao;

    @Override
    public void run(String... args) throws Exception {
        // 读取数据库已经添加应用 构造采集任务
        List<Monitor> monitors = monitorDao.findMonitorsByStatusNotInAndAndJobIdNotNull(Arrays.asList((byte)0, (byte)4));
        for (Monitor monitor : monitors) {
            try {
                // 构造采集任务Job实体
                Job appDefine = appService.getAppDefine(monitor.getApp());
                appDefine.setId(monitor.getJobId());
                appDefine.setMonitorId(monitor.getId());
                appDefine.setInterval(monitor.getIntervals());
                appDefine.setCyclic(true);
                appDefine.setTimestamp(System.currentTimeMillis());
                List<Param> params = paramDao.findParamsByMonitorId(monitor.getId());
                List<Configmap> configmaps = params.stream().map(param ->
                        new Configmap(param.getField(), param.getValue(), param.getType())).collect(Collectors.toList());
                List<ParamDefine> paramDefaultValue = appDefine.getParams().stream()
                                                              .filter(item -> StringUtils.hasText(item.getDefaultValue()))
                                                              .collect(Collectors.toList());
                paramDefaultValue.forEach(defaultVar -> {
                    if (configmaps.stream().noneMatch(item -> item.getKey().equals(defaultVar.getField()))) {
                        // todo type
                        Configmap configmap = new Configmap(defaultVar.getField(), defaultVar.getDefaultValue(), (byte) 1);
                        configmaps.add(configmap);
                    }
                });
                appDefine.setConfigmap(configmaps);
                // 下发采集任务
                long jobId = collectJobService.addAsyncCollectJob(appDefine);
                monitor.setJobId(jobId);
                monitorDao.save(monitor);
            } catch (Exception e) {
                log.error("init monitor job: {} error,continue next monitor", monitor, e);
            }
        }
    }
}
