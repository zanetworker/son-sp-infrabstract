/*
 * Copyright (c) 2015 SONATA-NFV, UCL, NOKIA, NCSR Demokritos ALL RIGHTS RESERVED.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Neither the name of the SONATA-NFV, UCL, NOKIA, NCSR Demokritos nor the names of its contributors
 * may be used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * This work has been performed in the framework of the SONATA project, funded by the European
 * Commission under Grant number 671517 through the Horizon 2020 and 5G-PPP programmes. The authors
 * would like to acknowledge the contributions of their colleagues of the SONATA partner consortium
 * (www.sonata-nfv.eu).
 *
 * @author Dario Valocchi (Ph.D.), UCL
 *
 */

package sonata.kernel.vimadaptor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import sonata.kernel.vimadaptor.AdaptorCore;
import sonata.kernel.vimadaptor.commons.FunctionDeployPayload;
import sonata.kernel.vimadaptor.commons.FunctionDeployResponse;
import sonata.kernel.vimadaptor.commons.NetworkConfigurePayload;
import sonata.kernel.vimadaptor.commons.ResourceAvailabilityData;
import sonata.kernel.vimadaptor.commons.ServiceDeployPayload;
import sonata.kernel.vimadaptor.commons.ServiceDeployResponse;
import sonata.kernel.vimadaptor.commons.ServicePreparePayload;
import sonata.kernel.vimadaptor.commons.SonataManifestMapper;
import sonata.kernel.vimadaptor.commons.Status;
import sonata.kernel.vimadaptor.commons.VimPreDeploymentList;
import sonata.kernel.vimadaptor.commons.VimResources;
import sonata.kernel.vimadaptor.commons.VnfImage;
import sonata.kernel.vimadaptor.commons.VnfRecord;
import sonata.kernel.vimadaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.vimadaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.vimadaptor.commons.vnfd.Unit.MemoryUnit;
import sonata.kernel.vimadaptor.messaging.ServicePlatformMessage;
import sonata.kernel.vimadaptor.messaging.TestConsumer;
import sonata.kernel.vimadaptor.messaging.TestProducer;
import sonata.kernel.vimadaptor.wrapper.WrapperConfiguration;
import sonata.kernel.vimadaptor.wrapper.ovsWrapper.OvsWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Unit test for simple App.
 */

public class DeployServiceTest implements MessageReceiver {
  private String output = null;
  private Object mon = new Object();
  private TestConsumer consumer;
  private String lastHeartbeat;
  private VnfDescriptor vtcVnfd;
  private VnfDescriptor vfwVnfd;
  private ServiceDeployPayload data;
  private ServiceDeployPayload dataV1;
  private ServiceDeployPayload data1V1;
  private ObjectMapper mapper;

  /**
   * Set up the test environment
   *
   */
  @Before
  public void setUp() throws Exception {

    ServiceDescriptor sd;
    StringBuilder bodyBuilder = new StringBuilder();
    BufferedReader in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/sonata-demo.yml")), Charset.forName("UTF-8")));
    String line;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    this.mapper = SonataManifestMapper.getSonataMapper();
    // this.mapper = new ObjectMapper(new YAMLFactory());
    // SimpleModule module = new SimpleModule();
    // module.addDeserializer(Unit.class, new UnitDeserializer());
    // //module.addDeserializer(VmFormat.class, new VmFormatDeserializer());
    // //module.addDeserializer(ConnectionPointType.class, new ConnectionPointTypeDeserializer());
    // mapper.registerModule(module);
    // mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    // mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
    // mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    // mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    // mapper.setSerializationInclusion(Include.NON_NULL);

    sd = mapper.readValue(bodyBuilder.toString(), ServiceDescriptor.class);

    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/vtc-vnf-vnfd.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    vtcVnfd = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/fw-vnf-vnfd.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    vfwVnfd = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);


    this.data = new ServiceDeployPayload();

    data.setServiceDescriptor(sd);
    data.addVnfDescriptor(vtcVnfd);
    data.addVnfDescriptor(vfwVnfd);

    // Set a second data for the demo payload

    sd = null;
    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/sonata-demo1-old.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    sd = mapper.readValue(bodyBuilder.toString(), ServiceDescriptor.class);

    VnfDescriptor vnfd1 = null;
    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/vtc-vnf-vnfd-old.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    vnfd1 = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    this.data1V1 = new ServiceDeployPayload();

    data1V1.setServiceDescriptor(sd);
    data1V1.addVnfDescriptor(vnfd1);


    sd = null;
    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/sonata-demo-old.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    sd = mapper.readValue(bodyBuilder.toString(), ServiceDescriptor.class);

    vnfd1 = null;
    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/vtc-vnf-vnfd-old.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    vnfd1 = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    VnfDescriptor vnfd2 = null;;
    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/fw-vnf-vnfd-old.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    vnfd2 = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    dataV1 = new ServiceDeployPayload();
    dataV1.setServiceDescriptor(sd);
    dataV1.addVnfDescriptor(vnfd1);
    dataV1.addVnfDescriptor(vnfd2);

  }

  /**
   * Test the checkResource API with the mock wrapper.
   *
   * @throws IOException
   * @throws InterruptedException
   */
  @Test
  public void testCheckResources() throws IOException, InterruptedException {

    BlockingQueue<ServicePlatformMessage> muxQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();
    BlockingQueue<ServicePlatformMessage> dispatcherQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();

    TestProducer producer = new TestProducer(muxQueue, this);
    consumer = new TestConsumer(dispatcherQueue);
    AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

    core.start();
    int counter = 0;

    try {
      while (counter < 2) {
        synchronized (mon) {
          mon.wait();
          if (lastHeartbeat.contains("RUNNING")) counter++;
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }

    String message =
        "{\"vim_type\":\"mock\",\"vim_address\":\"http://localhost:9999\",\"username\":\"Eve\","
            + "\"pass\":\"Operator\",\"city\":\"London\",\"country\":\"\","
            + "\"configuration\":{\"tenant\":\"operator\",\"tenant_ext_net\":\"ext-subnet\",\"tenant_ext_router\":\"ext-router\"}}";
    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(message, "application/json",
        topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("request_status");
    String wrUuid = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("Mock Wrapper added, with uuid: " + wrUuid);

    ResourceAvailabilityData data = new ResourceAvailabilityData();

    data.setCpu(4);
    data.setMemory(10);
    data.setMemoryUnit(MemoryUnit.GB);
    data.setStorage(50);
    data.setStorageUnit(MemoryUnit.GB);
    topic = "infrastructure.management.compute.resourceAvailability";


    message = mapper.writeValueAsString(data);

    ServicePlatformMessage checkResourcesMessage = new ServicePlatformMessage(message,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    output = null;
    consumer.injectMessage(checkResourcesMessage);
    Thread.sleep(2000);
    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    Assert.assertTrue(output.contains("OK"));
    message = "{\"uuid\":\"" + wrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);
    output = null;
    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }


    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));
    core.stop();

  }

  /**
   * test the service deployment API call with the mockWrapper.
   *
   * @throws IOException
   * @throws InterruptedException
   */
  @Test
  public void testDeployServiceMock() throws IOException, InterruptedException {


    BlockingQueue<ServicePlatformMessage> muxQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();
    BlockingQueue<ServicePlatformMessage> dispatcherQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();

    TestProducer producer = new TestProducer(muxQueue, this);
    consumer = new TestConsumer(dispatcherQueue);
    AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

    core.start();
    int counter = 0;

    try {
      while (counter < 2) {
        synchronized (mon) {
          mon.wait();
          if (lastHeartbeat.contains("RUNNING")) counter++;
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }


    String message =
        "{\"vim_type\":\"mock\",\"vim_address\":\"http://localhost:9999\",\"username\":\"Eve\","
            + "\"pass\":\"Operator\",\"city\":\"London\",\"country\":\"\","
            + "\"configuration\":{\"tenant\":\"operator\",\"tenant_ext_net\":\"ext-subnet\",\"tenant_ext_router\":\"ext-router\"}}";
    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(message, "application/json",
        topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("request_status");
    String wrUuid = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("Mock Wrapper added, with uuid: " + wrUuid);

    output = null;
    data.setVimUuid(wrUuid);

    String body = mapper.writeValueAsString(data);

    topic = "infrastructure.service.deploy";
    ServicePlatformMessage deployServiceMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(deployServiceMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    int retry = 0;
    int maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry)
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }

    Assert.assertTrue("No Deploy service response received", retry < maxRetry);

    ServiceDeployResponse response = mapper.readValue(output, ServiceDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("DEPLOYED"));
    Assert.assertTrue(response.getNsr().getStatus() == Status.normal_operation);

    for (VnfRecord vnfr : response.getVnfrs())
      Assert.assertTrue(vnfr.getStatus() == Status.normal_operation);
    output = null;
    message = "{\"uuid\":\"" + wrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));
    core.stop();

  }

  /**
   * This test is de-activated, if you want to use it with your NFVi-PoP, please edit the addVimBody
   * and addNetVimBody String Member to match your OpenStack and ovs configuration and substitute
   * the @Ignore annotation with the @Test annotation
   *
   * @throws Exception
   */
  @Ignore
  public void testDeployServiceOpenStackV1() throws Exception {

    BlockingQueue<ServicePlatformMessage> muxQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();
    BlockingQueue<ServicePlatformMessage> dispatcherQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();

    TestProducer producer = new TestProducer(muxQueue, this);
    consumer = new TestConsumer(dispatcherQueue);
    AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

    core.start();
    int counter = 0;

    try {
      while (counter < 2) {
        synchronized (mon) {
          mon.wait(); // String addNetVimBody = "{\"vim_type\":\"ovs\", "
          // + "\"vim_address\":\"10.100.32.200\",\"username\":\"operator\","
          // + "\"pass\":\"apass\",\"tenant\":\"tenant\",\"compute_uuid\":\"" + computeWrUuid +
          // "\"}";

          if (lastHeartbeat.contains("RUNNING")) counter++;
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }

    // PoP Athens.200 Mitaka
    String addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
        + "\"tenant_ext_router\":\"e8cdd5c7-191f-4215-83f3-53ee1113db86\", "
        + "\"tenant_ext_net\":\"53d43a3e-8c86-48e6-b1cb-f1f2c48833de\"," + "\"tenant\":\"admin\""
        + "}," + "\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"sonata.dem\","
        + "\"pass\":\"s0nata.d3m\"}";

    // PoP Athens.10 Mitaka
    // String addVimBody = "{\"vim_type\":\"Heat\", "
    // + "\"configuration\":{"
    // + "\"tenant_ext_router\":\"2c2a8b09-b746-47de-b0ce-dce5fa242c7e\", "
    // + "\"tenant_ext_net\":\"12bf4db8-0131-4322-bd22-0b1ad8333748\","
    // + "\"tenant\":\"sonata.dem\""
    // + "},"
    // + "\"city\":\"Athens\",\"country\":\"Greece\","
    // + "\"vim_address\":\"10.100.32.10\",\"username\":\"sonata.dem\","
    // + "\"pass\":\"s0n@t@.dem\"}";

    // PoP Aveiro Mitaka
    // String addVimBody = "{\"vim_type\":\"Heat\", "
    // + "\"configuration\":{"
    // + "\"tenant_ext_router\":\"0e5d6e42-e544-4ec3-8ce1-9ac950ae994b\", "
    // + "\"tenant_ext_net\":\"c999f013-2022-4464-b44f-88f4437f23b0\","
    // + "\"tenant\":\"son-demo\""
    // + "},"
    // + "\"city\":\"Aveiro\",\"country\":\"Portugal\","
    // + "\"vim_address\":\"172.31.6.9\",\"username\":\"son-demo\","
    // + "\"pass\":\"S0n-D3m0\"}";


    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(addVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }



    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("request_status");
    String computeWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid);


    output = null;
    // PoP Athens .10
    // String addNetVimBody = "{\"vim_type\":\"ovs\", "
    // +
    // "\"vim_address\":\"10.100.32.10\",\"username\":\"operator\",\"city\":\"Athens\",\"country\":\"Greece\","
    // + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid + "\"}}";

    // PoP Athens .200
    String addNetVimBody = "{\"vim_type\":\"ovs\", "
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"operator\",\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid + "\"}}";

    // PoP Aveiro
    // String addNetVimBody = "{\"vim_type\":\"ovs\", "
    // +
    // "\"vim_address\":\"172.31.6.9\",\"username\":\"operator\",\"city\":\"Aveiro\",\"country\":\"Portugal\","
    // + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid + "\"}}";

    topic = "infrastructure.management.network.add";
    ServicePlatformMessage addNetVimMessage = new ServicePlatformMessage(addNetVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addNetVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String netWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue("Failed to add the ovs wrapper. Status " + status,
        status.equals("COMPLETED"));
    System.out.println("OpenDaylight Wrapper added, with uuid: " + netWrUuid);


    output = null;
    String instanceUuid = dataV1.getNsd().getInstanceUuid();
    dataV1.setVimUuid(computeWrUuid);
    String body = mapper.writeValueAsString(dataV1);

    topic = "infrastructure.service.deploy";
    ServicePlatformMessage deployServiceMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(deployServiceMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    int retry = 0;
    int maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry)
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }

    System.out.println("ServiceDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No Deploy service response received", retry < maxRetry);
    ServiceDeployResponse response = mapper.readValue(output, ServiceDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
    Assert.assertTrue(response.getNsr().getStatus() == Status.offline);

    for (VnfRecord vnfr : response.getVnfrs())
      Assert.assertTrue(vnfr.getStatus() == Status.offline);


    // SFC deconfiguration
    // 1. De-configure SFC
    output = null;
    String message = "{\"service_instance_id\":\"" + instanceUuid + "\"}";
    topic = "infrastructure.service.chain.deconfigure";
    ServicePlatformMessage deconfigureNetworkMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(deconfigureNetworkMessage);
    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(2000);
          System.out.println(output);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));
    // Service removal
    output = null;
    message = "{\"instance_uuid\":\"" + instanceUuid + "\"}";
    topic = "infrastructure.service.remove";
    ServicePlatformMessage removeInstanceMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeInstanceMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(2000);
        System.out.println(output);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));

    // VIM removal
    output = null;
    message = "{\"uuid\":\"" + computeWrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));
    output = null;
    message = "{\"uuid\":\"" + netWrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeNetVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeNetVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    core.stop();

  }

  /**
   * This test is de-activated, if you want to use it with your NFVi-PoP, please edit the addVimBody
   * String Member to match your OpenStack configuration and substitute the @ignore annotation with
   * the @test annotation
   *
   * @throws Exception
   */
  @Ignore
  public void testDeployTwoServicesOpenStackV1() {


    BlockingQueue<ServicePlatformMessage> muxQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();
    BlockingQueue<ServicePlatformMessage> dispatcherQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();

    TestProducer producer = new TestProducer(muxQueue, this);
    consumer = new TestConsumer(dispatcherQueue);
    AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

    try {
      core.start();
    } catch (IOException e1) {
      e1.printStackTrace();
      return;
    }

    int counter = 0;

    try {
      while (counter < 2) {
        synchronized (mon) {
          mon.wait();
          if (lastHeartbeat.contains("RUNNING")) counter++;
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }


    // Add first PoP
    // PoP Athens.200 Mitaka
    String addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
        + "\"tenant_ext_router\":\"e8cdd5c7-191f-4215-83f3-53ee1113db86\", "
        + "\"tenant_ext_net\":\"53d43a3e-8c86-48e6-b1cb-f1f2c48833de\"," + "\"tenant\":\"admin\""
        + "}," + "\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"sonata.dem\","
        + "\"pass\":\"s0nata.d3m\"}";
    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(addVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    try {
      Thread.sleep(2000);

      while (output == null)
        synchronized (mon) {
          mon.wait(1000);
        }

    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("request_status");
    String computeWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid);


    output = null;
    String addNetVimBody = "{\"vim_type\":\"ovs\", "
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"operator\",\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid + "\"}}";
    topic = "infrastructure.management.network.add";
    ServicePlatformMessage addNetVimMessage = new ServicePlatformMessage(addNetVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addNetVimMessage);
    try {
      Thread.sleep(2000);

      while (output == null)
        synchronized (mon) {
          mon.wait(1000);
        }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String netWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue("Failed to add the ovs wrapper. Status " + status,
        status.equals("COMPLETED"));
    System.out.println("Openvswitch Wrapper added, with uuid: " + netWrUuid);


    output = null;

    String baseInstanceUuid = dataV1.getNsd().getInstanceUuid();
    dataV1.setVimUuid(computeWrUuid);
    dataV1.getNsd().setInstanceUuid(baseInstanceUuid + "-01");

    String body;
    try {
      body = mapper.writeValueAsString(dataV1);
    } catch (JsonProcessingException e1) {
      e1.printStackTrace();
      return;
    }

    topic = "infrastructure.service.deploy";
    ServicePlatformMessage deployServiceMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(deployServiceMessage);
    int retry = 0;
    int maxRetry = 60;
    try {
      Thread.sleep(2000);
      while (output == null)
        synchronized (mon) {
          mon.wait(1000);
        }
      Assert.assertNotNull(output);


      while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry)
        synchronized (mon) {
          mon.wait(1000);
          retry++;
        }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    Assert.assertTrue("No Deploy service response received", retry < maxRetry);
    ServiceDeployResponse response;
    try {
      response = mapper.readValue(output, ServiceDeployResponse.class);

      System.out.println("output:");
      System.out.println(output);
      Assert.assertTrue("Deploy request failed with status: " + response.getRequestStatus()
          + " - Message: " + response.getMessage(),
          response.getRequestStatus().equals("COMPLETED"));
      if (response.getNsr() != null) {
        Assert.assertTrue(response.getNsr().getStatus() == Status.offline);
      }
      if (response.getVnfrs() != null) {
        for (VnfRecord vnfr : response.getVnfrs())
          Assert.assertTrue(vnfr.getStatus() == Status.offline);
      }
    } catch (JsonParseException e) {
      e.printStackTrace();
    } catch (JsonMappingException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Deploy a second instance of the same service

    data1V1.getNsd().setInstanceUuid(baseInstanceUuid + "-02");
    data1V1.setVimUuid(computeWrUuid);
    output = null;

    try {
      body = mapper.writeValueAsString(data1V1);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return;
    }

    topic = "infrastructure.service.deploy";
    deployServiceMessage = new ServicePlatformMessage(body, "application/x-yaml", topic,
        UUID.randomUUID().toString(), topic);

    consumer.injectMessage(deployServiceMessage);

    try {
      Thread.sleep(2000);

      while (output == null)
        synchronized (mon) {
          mon.wait(1000);
        }
      Assert.assertNotNull(output);
      retry = 0;
      while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry)
        synchronized (mon) {
          mon.wait(1000);
          retry++;
        }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    System.out.println("ServiceDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No Deploy service response received", retry < maxRetry);
    try {
      response = mapper.readValue(output, ServiceDeployResponse.class);

      Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
      if (response.getNsr() != null) {
        Assert.assertTrue(response.getNsr().getStatus() == Status.offline);
      }
      if (response.getVnfrs() != null) {
        for (VnfRecord vnfr : response.getVnfrs()) {
          Assert.assertTrue(vnfr.getStatus() == Status.offline);
        }
      }
    } catch (Exception e){
      e.printStackTrace();
    }
    // // Clean the OpenStack tenant from the stack
    // OpenStackHeatClient client =
    // new OpenStackHeatClient("143.233.127.3", "op_sonata", "op_s0n@t@", "op_sonata");
    // String stackName = response.getInstanceName();
    //
    // String deleteStatus = client.deleteStack(stackName, response.getInstanceVimUuid());
    // assertNotNull("Failed to delete stack", deleteStatus);
    //
    // if (deleteStatus != null) {
    // System.out.println("status of deleted stack " + stackName + " is " + deleteStatus);
    // assertEquals("DELETED", deleteStatus);
    // }


    // Service removal
    System.out.println("Removing services...");
    output = null;
    String instanceUuid = baseInstanceUuid + "-01";
    String message =
        "{\"instance_uuid\":\"" + instanceUuid + "\",\"vim_uuid\":\"" + computeWrUuid + "\"}";
    topic = "infrastructure.service.remove";
    ServicePlatformMessage removeInstanceMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeInstanceMessage);

    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(2000);
          System.out.println(output);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));
    output = null;
    instanceUuid = baseInstanceUuid + "-02";
    message = "{\"instance_uuid\":\"" + instanceUuid + "\",\"vim_uuid\":\"" + computeWrUuid + "\"}";
    topic = "infrastructure.service.remove";
    removeInstanceMessage = new ServicePlatformMessage(message, "application/json", topic,
        UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeInstanceMessage);
    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(2000);
          System.out.println(output);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));



    output = null;
    message = "{\"uuid\":\"" + computeWrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);
    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(1000);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));
    output = null;
    message = "{\"uuid\":\"" + netWrUuid + "\"}";
    topic = "infrastructure.management.network.remove";
    ServicePlatformMessage removeNetVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeNetVimMessage);

    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(1000);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    core.stop();


    // clean the SFC engine
    System.out.println("Cleaning the SFC environment...");
    WrapperConfiguration config = new WrapperConfiguration();

    config.setVimEndpoint("10.100.32.200");

    OvsWrapper wrapper = new OvsWrapper(config);
    try {
      wrapper.deconfigureNetworking(dataV1.getNsd().getInstanceUuid());
      wrapper.deconfigureNetworking(data1V1.getNsd().getInstanceUuid());
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  /**
   * This test is de-activated, if you want to use it with your NFVi-PoP, please edit the addVimBody
   * String Member to match your OpenStack configuration and substitute the @ignore annotation with
   * the @test annotation
   *
   * @throws Exception
   */
  @Ignore
  public void testDeployServiceIncremental() throws Exception {
    BlockingQueue<ServicePlatformMessage> muxQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();
    BlockingQueue<ServicePlatformMessage> dispatcherQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();

    TestProducer producer = new TestProducer(muxQueue, this);
    consumer = new TestConsumer(dispatcherQueue);
    AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

    core.start();
    int counter = 0;

    try {
      while (counter < 2) {
        synchronized (mon) {
          mon.wait();
          if (lastHeartbeat.contains("RUNNING")) counter++;
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }


    // Add first PoP
    // PoP Athens.200 Mitaka
    String addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
        + "\"tenant_ext_router\":\"e8cdd5c7-191f-4215-83f3-53ee1113db86\", "
        + "\"tenant_ext_net\":\"53d43a3e-8c86-48e6-b1cb-f1f2c48833de\"," + "\"tenant\":\"admin\""
        + "}," + "\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"sonata.dem\","
        + "\"pass\":\"s0nata.d3m\"}";

    System.out.println("[TwoPoPTest] Adding PoP .200");
    // Add first PoP
    // PoP Athens.201 Newton
    // String addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
    // + "\"tenant_ext_router\":\"3bc4fc5c-9c3e-4f29-8244-267fbc2c7ccb\", "
    // + "\"tenant_ext_net\":\"081e13ad-e231-4291-a390-4a66fa09b846\"," + "\"tenant\":\"admin\""
    // + "}," + "\"city\":\"Athens\",\"country\":\"Greece\","
    // + "\"vim_address\":\"10.30.0.201\",\"username\":\"admin\","
    // + "\"pass\":\"char1234\"}";

    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(addVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }



    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("request_status");
    String computeWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid);


    output = null;
    String addNetVimBody = "{\"vim_type\":\"ovs\", "
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"operator\",\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid + "\"}}";
    topic = "infrastructure.management.network.add";
    ServicePlatformMessage addNetVimMessage = new ServicePlatformMessage(addNetVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addNetVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String netWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue("Failed to add the ovs wrapper. Status " + status,
        status.equals("COMPLETED"));
    System.out.println("OVS Wrapper added, with uuid: " + netWrUuid);


    output = null;

    // Prepare the system for a service deployment

    ServicePreparePayload payload = new ServicePreparePayload();

    payload.setInstanceId(data.getNsd().getInstanceUuid());
    ArrayList<VimPreDeploymentList> vims = new ArrayList<VimPreDeploymentList>();
    VimPreDeploymentList vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid(computeWrUuid);
    ArrayList<VnfImage> vnfImages = new ArrayList<VnfImage>();
    VnfImage vtcImgade = new VnfImage("eu.sonata-nfv_vtc-vnf_0.1_vdu01",
        "http://download.cirros-cloud.net/0.3.5/cirros-0.3.5-x86_64-disk.img");
    vnfImages.add(vtcImgade);
    VnfImage vfwImgade = new VnfImage("eu.sonata-nfv_fw-vnf_0.1_1",
        "http://download.cirros-cloud.net/0.3.5/cirros-0.3.5-x86_64-disk.img");
    vnfImages.add(vfwImgade);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);

    payload.setVimList(vims);

    String body = mapper.writeValueAsString(payload);

    topic = "infrastructure.service.prepare";
    ServicePlatformMessage servicePrepareMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(servicePrepareMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String message = jsonObject.getString("message");
    Assert.assertTrue("Failed to prepare the environment for the service deployment: " + status
        + " - message: " + message, status.equals("COMPLETED"));
    System.out.println("Service " + payload.getInstanceId() + " ready for deployment");


    // Send a VNF instantiation request for each VNFD linked by the NSD
    ArrayList<VnfRecord> records = new ArrayList<VnfRecord>();
    for (VnfDescriptor vnfd : data.getVnfdList()) {

      output = null;

      FunctionDeployPayload vnfPayload = new FunctionDeployPayload();
      vnfPayload.setVnfd(vnfd);
      vnfPayload.setVimUuid(computeWrUuid);
      vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
      body = mapper.writeValueAsString(vnfPayload);

      topic = "infrastructure.function.deploy";
      ServicePlatformMessage functionDeployMessage = new ServicePlatformMessage(body,
          "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

      consumer.injectMessage(functionDeployMessage);

      Thread.sleep(2000);
      while (output == null)
        synchronized (mon) {
          mon.wait(1000);
        }
      Assert.assertNotNull(output);
      int retry = 0;
      int maxRetry = 60;
      while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
        synchronized (mon) {
          mon.wait(1000);
          retry++;
        }
      }

      System.out.println("FunctionDeployResponse: ");
      System.out.println(output);
      Assert.assertTrue("No response received after function deployment", retry < maxRetry);
      FunctionDeployResponse response = mapper.readValue(output, FunctionDeployResponse.class);
      Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
      Assert.assertTrue(response.getVnfr().getStatus() == Status.offline);
      records.add(response.getVnfr());
    }

    // Finally configure Networking in each NFVi-PoP (VIMs)

    output = null;

    NetworkConfigurePayload netPayload = new NetworkConfigurePayload();
    netPayload.setNsd(data.getNsd());
    netPayload.setVnfds(data.getVnfdList());
    netPayload.setVnfrs(records);
    netPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());


    body = mapper.writeValueAsString(netPayload);

    topic = "infrastructure.service.chain.configure";
    ServicePlatformMessage networkConfigureMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(networkConfigureMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Failed to configure inter-PoP SFC. status:" + status,
        status.equals("COMPLETED"));
    System.out.println(
        "Service " + payload.getInstanceId() + " deployed and configured in selected VIM(s)");

    // Clean everything:
    // 1. De-configure SFC
    output = null;
    message = "{\"service_instance_id\":\"" + data.getNsd().getInstanceUuid() + "\"}";
    topic = "infrastructure.service.chain.deconfigure";
    ServicePlatformMessage deconfigureNetworkMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(deconfigureNetworkMessage);
    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(2000);
          System.out.println(output);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));

    // 2. Remove Service
    // Service removal
    output = null;
    String instanceUuid = data.getNsd().getInstanceUuid();
    message = "{\"instance_uuid\":\"" + instanceUuid + "\"}";
    topic = "infrastructure.service.remove";
    ServicePlatformMessage removeInstanceMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeInstanceMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(2000);
        System.out.println(output);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));

    // 3. De-register VIMs.

    output = null;
    message = "{\"uuid\":\"" + computeWrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    output = null;
    message = "{\"uuid\":\"" + netWrUuid + "\"}";
    topic = "infrastructure.management.network.remove";
    ServicePlatformMessage removeNetVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeNetVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    core.stop();


  }


  /**
   * This test is de-activated, if you want to use it with your NFVi-PoPs (at least two), please
   * edit the addVimBody String member to match your OpenStack configuration and substitute
   * the @ignore annotation with the @test annotation
   *
   * @throws Exception
   */
  @Ignore
  public void testDeployServiceIncrementalMultiPoP() throws Exception {
    BlockingQueue<ServicePlatformMessage> muxQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();
    BlockingQueue<ServicePlatformMessage> dispatcherQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();

    TestProducer producer = new TestProducer(muxQueue, this);
    consumer = new TestConsumer(dispatcherQueue);
    AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

    core.start();
    int counter = 0;

    try {
      while (counter < 2) {
        synchronized (mon) {
          mon.wait();
          if (lastHeartbeat.contains("RUNNING")) counter++;
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }

    System.out.println("[TwoPoPTest] Adding PoP .200");
    // Add first PoP
    // PoP Athens.200 Mitaka
    String addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
        + "\"tenant_ext_router\":\"e8cdd5c7-191f-4215-83f3-53ee1113db86\", "
        + "\"tenant_ext_net\":\"53d43a3e-8c86-48e6-b1cb-f1f2c48833de\"," + "\"tenant\":\"admin\""
        + "}," + "\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"sonata.dem\","
        + "\"pass\":\"s0nata.d3m\"}";



    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(addVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }



    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("request_status");
    String computeWrUuid1 = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid1);


    output = null;
    String addNetVimBody = "{\"vim_type\":\"ovs\", "
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"operator\",\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid1 + "\"}}";
    topic = "infrastructure.management.network.add";
    ServicePlatformMessage addNetVimMessage = new ServicePlatformMessage(addNetVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addNetVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String netWrUuid1 = jsonObject.getString("uuid");
    Assert.assertTrue("Failed to add the ovs wrapper. Status " + status,
        status.equals("COMPLETED"));
    System.out.println("OVS Wrapper added, with uuid: " + netWrUuid1);


    output = null;

    // Add second PoP
    System.out.println("[TwoPoPTest] Adding PoP .10");
    // PoP Athens.10 Mitaka
    addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
        + "\"tenant_ext_router\":\"2c2a8b09-b746-47de-b0ce-dce5fa242c7e\", "
        + "\"tenant_ext_net\":\"12bf4db8-0131-4322-bd22-0b1ad8333748\","
        + "\"tenant\":\"sonata.dem\"" + "}," + "\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"vim_address\":\"10.100.32.10\",\"username\":\"sonata.dem\","
        + "\"pass\":\"s0n@t@.dem\"}";

    topic = "infrastructure.management.compute.add";
    addVimMessage = new ServicePlatformMessage(addVimBody, "application/json", topic,
        UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }



    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    String computeWrUuid2 = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid2);


    output = null;
    addNetVimBody = "{\"vim_type\":\"ovs\", "
        + "\"vim_address\":\"10.100.32.10\",\"username\":\"operator\",\"city\":\"Athens\",\"country\":\"Greece\","
        + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid2 + "\"}}";
    topic = "infrastructure.management.network.add";
    addNetVimMessage = new ServicePlatformMessage(addNetVimBody, "application/json", topic,
        UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addNetVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String netWrUuid2 = jsonObject.getString("uuid");
    Assert.assertTrue("Failed to add the ovs wrapper. Status " + status,
        status.equals("COMPLETED"));
    System.out.println("OVS Wrapper added, with uuid: " + netWrUuid2);


    output = null;

    // List available PoP
    System.out.println("[TwoPoPTest] Listing available NFVIi-PoP.");

    topic = "infrastructure.management.compute.list";
    ServicePlatformMessage listVimMessage =
        new ServicePlatformMessage(null, null, topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(listVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    VimResources[] vimList = mapper.readValue(output, VimResources[].class);
    System.out.println("[TwoPoPTest] Listing available PoP");
    for (VimResources resource : vimList) {
      System.out.println(mapper.writeValueAsString(resource));
    }

    output = null;
    // Prepare the system for a service deployment
    System.out.println("[TwoPoPTest] Building service.prepare call.");

    ServicePreparePayload payload = new ServicePreparePayload();

    payload.setInstanceId(data.getNsd().getInstanceUuid());
    ArrayList<VimPreDeploymentList> vims = new ArrayList<VimPreDeploymentList>();
    VimPreDeploymentList vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid(computeWrUuid1);
    ArrayList<VnfImage> vnfImages = new ArrayList<VnfImage>();
    VnfImage vtcImgade =
        // new VnfImage("eu.sonata-nfv_vtc-vnf_0.1_vdu01", "file:///test_images/sonata-vtc.img");
        new VnfImage("eu.sonata-nfv_vtc-vnf_0.1_vdu01",
            "http://download.cirros-cloud.net/0.3.5/cirros-0.3.5-x86_64-disk.img");

    vnfImages.add(vtcImgade);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);



    vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid(computeWrUuid2);
    vnfImages = new ArrayList<VnfImage>();
    VnfImage vfwImgade =
        // new VnfImage("eu.sonata-nfv_fw-vnf_0.1_1", "file:///test_images/sonata-vfw.img");
        new VnfImage("eu.sonata-nfv_fw-vnf_0.1_1",
            "http://download.cirros-cloud.net/0.3.5/cirros-0.3.5-x86_64-disk.img");
    vnfImages.add(vfwImgade);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);

    payload.setVimList(vims);

    String body = mapper.writeValueAsString(payload);
    System.out.println("[TwoPoPTest] Request body:");
    System.out.println(body);

    topic = "infrastructure.service.prepare";
    ServicePlatformMessage servicePrepareMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(servicePrepareMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String message = jsonObject.getString("message");
    Assert.assertTrue("Failed to prepare the environment for the service deployment: " + status
        + " - message: " + message, status.equals("COMPLETED"));
    System.out.println("Service " + payload.getInstanceId() + " ready for deployment");


    // Deploy the two VNFs, one in each PoP
    ArrayList<VnfRecord> records = new ArrayList<VnfRecord>();

    // vTC VNF in PoP#1
    output = null;

    FunctionDeployPayload vnfPayload = new FunctionDeployPayload();
    vnfPayload.setVnfd(vtcVnfd);
    vnfPayload.setVimUuid(computeWrUuid1);
    vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
    body = mapper.writeValueAsString(vnfPayload);

    topic = "infrastructure.function.deploy";
    ServicePlatformMessage functionDeployMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(functionDeployMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    int retry = 0;
    int maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }
    }

    System.out.println("FunctionDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No response received after function deployment", retry < maxRetry);
    FunctionDeployResponse response = mapper.readValue(output, FunctionDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
    Assert.assertTrue(response.getVnfr().getStatus() == Status.offline);
    records.add(response.getVnfr());

    // vFw VNF in PoP#2
    output = null;
    response = null;

    vnfPayload = new FunctionDeployPayload();
    vnfPayload.setVnfd(vfwVnfd);
    vnfPayload.setVimUuid(computeWrUuid2);
    vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
    body = mapper.writeValueAsString(vnfPayload);

    topic = "infrastructure.function.deploy";
    functionDeployMessage = new ServicePlatformMessage(body, "application/x-yaml", topic,
        UUID.randomUUID().toString(), topic);

    consumer.injectMessage(functionDeployMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    retry = 0;
    maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }
    }

    System.out.println("FunctionDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No response received after function deployment", retry < maxRetry);
    response = mapper.readValue(output, FunctionDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
    Assert.assertTrue(response.getVnfr().getStatus() == Status.offline);
    records.add(response.getVnfr());

    // Finally configure Networking in each NFVi-PoP (VIMs)

    output = null;

    NetworkConfigurePayload netPayload = new NetworkConfigurePayload();
    netPayload.setNsd(data.getNsd());
    netPayload.setVnfds(data.getVnfdList());
    netPayload.setVnfrs(records);
    netPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());


    body = mapper.writeValueAsString(netPayload);

    topic = "infrastructure.service.chain.configure";
    ServicePlatformMessage networkConfigureMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(networkConfigureMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Failed to configure inter-PoP SFC. status:" + status,
        status.equals("COMPLETED"));
    System.out.println(
        "Service " + payload.getInstanceId() + " deployed and configured in selected VIM(s)");

    output = null;

    // TODO WIM PART

    // De-configure SFC

    message = "{\"service_instance_id\":\"" + data.getNsd().getInstanceUuid() + "\"}";
    topic = "infrastructure.service.chain.deconfigure";
    ServicePlatformMessage deconfigureNetworkMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(deconfigureNetworkMessage);
    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(2000);
          System.out.println(output);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));

    output = null;

    // Remove service
    message = "{\"instance_uuid\":\"" + data.getNsd().getInstanceUuid() + "\"}";
    topic = "infrastructure.service.remove";
    ServicePlatformMessage removeInstanceMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeInstanceMessage);
    try {
      while (output == null) {
        synchronized (mon) {
          mon.wait(2000);
          System.out.println(output);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status,
        status.equals("COMPLETED"));

    // Remove registered VIMs

    output = null;
    message = "{\"uuid\":\"" + computeWrUuid1 + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    output = null;
    message = "{\"uuid\":\"" + netWrUuid1 + "\"}";
    topic = "infrastructure.management.network.remove";
    ServicePlatformMessage removeNetVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeNetVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    output = null;
    message = "{\"uuid\":\"" + computeWrUuid2 + "\"}";
    topic = "infrastructure.management.compute.remove";
    removeVimMessage = new ServicePlatformMessage(message, "application/json", topic,
        UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    output = null;
    message = "{\"uuid\":\"" + netWrUuid2 + "\"}";
    topic = "infrastructure.management.network.remove";
    removeNetVimMessage = new ServicePlatformMessage(message, "application/json", topic,
        UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeNetVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue(status.equals("COMPLETED"));

    core.stop();


  }



  @Ignore
  public void testPrepareServicePayload() throws JsonProcessingException {

    ServicePreparePayload payload = new ServicePreparePayload();

    payload.setInstanceId(data.getNsd().getInstanceUuid());
    ArrayList<VimPreDeploymentList> vims = new ArrayList<VimPreDeploymentList>();
    VimPreDeploymentList vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid("aaaa-aaaaaaaaaaaaa-aaaaaaaaaaaaa-aaaaaaaa");
    ArrayList<VnfImage> vnfImages = new ArrayList<VnfImage>();
    VnfImage Image1 = new VnfImage("eu.sonata-nfv:1-vnf:0.1:1", "file:///test_images/sonata-1");
    VnfImage Image2 = new VnfImage("eu.sonata-nfv:2-vnf:0.1:1", "file:///test_images/sonata-2");
    VnfImage Image3 = new VnfImage("eu.sonata-nfv:3-vnf:0.1:1", "file:///test_images/sonata-3");
    VnfImage Image4 = new VnfImage("eu.sonata-nfv:4-vnf:0.1:1", "file:///test_images/sonata-4");
    vnfImages.add(Image1);
    vnfImages.add(Image2);
    vnfImages.add(Image3);
    vnfImages.add(Image4);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);


    vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid("bbbb-bbbbbbbbbbbb-bbbbbbbbbbbb-bbbbbbbbb");
    vnfImages = new ArrayList<VnfImage>();
    VnfImage Image5 = new VnfImage("eu.sonata-nfv:5-vnf:0.1:1", "file:///test_images/sonata-5");
    VnfImage Image6 = new VnfImage("eu.sonata-nfv:6-vnf:0.1:1", "file:///test_images/sonata-6");
    VnfImage Image7 = new VnfImage("eu.sonata-nfv:7-vnf:0.1:1", "file:///test_images/sonata-7");
    vnfImages.add(Image5);
    vnfImages.add(Image6);
    vnfImages.add(Image7);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);

    payload.setVimList(vims);

    // System.out.println(mapper.writeValueAsString(payload));
  }

  public void receiveHeartbeat(ServicePlatformMessage message) {
    synchronized (mon) {
      this.lastHeartbeat = message.getBody();
      mon.notifyAll();
    }
  }

  public void receive(ServicePlatformMessage message) {
    synchronized (mon) {
      this.output = message.getBody();
      mon.notifyAll();
    }
  }

  public void forwardToConsumer(ServicePlatformMessage message) {
    consumer.injectMessage(message);
  }
}
