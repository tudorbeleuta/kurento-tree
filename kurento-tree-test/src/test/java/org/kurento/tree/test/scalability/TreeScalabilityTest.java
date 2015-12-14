/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package org.kurento.tree.test.scalability;

import static org.kurento.commons.PropertiesManager.getProperty;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.kurento.client.EventListener;
import org.kurento.client.MediaPipeline;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.test.base.KurentoTest;
import org.kurento.test.base.KurentoTreeTestBase;
import org.kurento.test.browser.Browser;
import org.kurento.test.browser.BrowserType;
import org.kurento.test.browser.WebPageType;
import org.kurento.test.browser.WebRtcChannel;
import org.kurento.test.browser.WebRtcMode;
import org.kurento.test.config.BrowserConfig;
import org.kurento.test.config.BrowserScope;
import org.kurento.test.config.TestScenario;
import org.kurento.test.latency.ChartWriter;
import org.kurento.test.latency.LatencyController;
import org.kurento.test.latency.LatencyRegistry;
import org.kurento.tree.client.KurentoTreeClient;
import org.kurento.tree.client.TreeEndpoint;
import org.kurento.tree.client.TreeException;

/**
 * <strong>Description</strong>: WebRTC one to one (plus N fake clients) with
 * tree.<br/>
 * <strong>Pipeline</strong>:
 * <ul>
 * <li>TreeSource -> TreeSink</li>
 * </ul>
 * <strong>Pass criteria</strong>:
 * <ul>
 * <li>Media should be received in the video tag</li>
 * <li>Color of the video should be as expected</li>
 * </ul>
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 6.1.1
 */
public class TreeScalabilityTest extends KurentoTreeTestBase {

	private static final int WIDTH = 500;
	private static final int HEIGHT = 270;

	private static int playTime = getProperty(
			"test.scalability.latency.playtime", 30); // seconds
	private static String[] fakeClientsArray = getProperty(
			"test.scalability.latency.fakeclients", "0,1,3,5").split(",");

	private static Map<Long, LatencyRegistry> latencyResult = new HashMap<>();

	@Parameter(1)
	public int fakeClients = 1;

	@Parameters(name = "{index}: {0}")
	public static Collection<Object[]> data() {
		String videoPath = KurentoTest.getTestFilesPath()
				+ "/video/15sec/rgbHD.y4m";
		TestScenario test = new TestScenario();
		test.addBrowser(BrowserConfig.BROWSER + 0,
				new Browser.Builder().webPageType(WebPageType.WEBRTC)
						.browserType(BrowserType.CHROME)
						.scope(BrowserScope.LOCAL).video(videoPath).build());
		test.addBrowser(BrowserConfig.BROWSER + 1,
				new Browser.Builder().webPageType(WebPageType.WEBRTC)
						.browserType(BrowserType.CHROME)
						.scope(BrowserScope.LOCAL).build());

		Collection<Object[]> out = new ArrayList<>();
		for (String s : fakeClientsArray) {
			out.add(new Object[] { test, Integer.parseInt(s) });
		}

		return out;
	}

	public void addFakeClients(int numMockClients,
			final KurentoTreeClient kurentoTree, final String treeId,
			final String sinkId) throws TreeException, IOException {

		MediaPipeline mockPipeline = fakeKurentoClient().createMediaPipeline();

		for (int i = 0; i < numMockClients; i++) {
			WebRtcEndpoint mockReceiver = new WebRtcEndpoint.Builder(
					mockPipeline).build();

			mockReceiver.addOnIceCandidateListener(
					new EventListener<OnIceCandidateEvent>() {
						@Override
						public void onEvent(OnIceCandidateEvent event) {
							try {
								kurentoTree.addIceCandidate(treeId, sinkId,
										event.getCandidate());

							} catch (Exception e) {
								log.error("Exception adding candidate", e);
							}
						}
					});

			String sdpOffer = mockReceiver.generateOffer();
			TreeEndpoint treeEndpoint = kurentoTree.addTreeSink(treeId,
					sdpOffer);
			String sdpAnswer = treeEndpoint.getSdp();
			mockReceiver.processAnswer(sdpAnswer);
			mockReceiver.gatherCandidates();
		}
	}

	@Test
	public void testTreeScalability() throws Exception {
		KurentoTreeClient kurentoTreeClientSink = null;

		String treeId = "myTree";

		try {

			// Creating tree
			kurentoTreeClient.createTree(treeId);
			kurentoTreeClientSink = new KurentoTreeClient(
					System.getProperty(KTS_WS_URI_PROP, KTS_WS_URI_DEFAULT));

			// Starting tree source
			getPage(0).setTreeSource(kurentoTreeClient, treeId,
					WebRtcChannel.AUDIO_AND_VIDEO, WebRtcMode.SEND_ONLY);

			// Starting tree sink
			getPage(1).subscribeEvents("playing");
			String sinkId = getPage(1).addTreeSink(kurentoTreeClientSink,
					treeId, WebRtcChannel.AUDIO_AND_VIDEO, WebRtcMode.RCV_ONLY);

			// Fake clients
			addFakeClients(fakeClients, kurentoTreeClient, treeId, sinkId);

			// Latency assessment
			LatencyController cs = new LatencyController();
			cs.checkLatency(playTime, TimeUnit.SECONDS, getPage(0),
					getPage(1));
			cs.drawChart(
					getDefaultOutputFile("-fakeClients" + fakeClients + ".png"),
					WIDTH, HEIGHT);
			cs.writeCsv(getDefaultOutputFile(
					"-fakeClients" + fakeClients + ".csv"));
			cs.logLatencyErrorrs();

			// Latency average
			long avgLatency = 0;
			Map<Long, LatencyRegistry> latencyMap = cs.getLatencyMap();
			for (LatencyRegistry lr : latencyMap.values()) {
				avgLatency += lr.getLatency();
			}

			int latencyMapSize = latencyMap.size();
			if (latencyMapSize > 0) {
				avgLatency /= latencyMap.size();
			}
			latencyResult.put((long) fakeClients,
					new LatencyRegistry(avgLatency));

		} finally {

			// Close tree client
			if (kurentoTreeClientSink != null) {
				kurentoTreeClientSink.close();
			}

			kurentoTreeClient.releaseTree(treeId);

			// Write csv
			PrintWriter pw = new PrintWriter(
					new FileWriter(getDefaultOutputFile("-latency.csv")));
			for (long time : latencyResult.keySet()) {
				pw.println(time + "," + latencyResult.get(time).getLatency());
			}
			pw.close();

			// Draw chart
			ChartWriter chartWriter = new ChartWriter(latencyResult,
					"Latency avg",
					"Latency of fake clients: "
							+ Arrays.toString(fakeClientsArray),
					"Number of client(s)", "Latency (ms)");
			chartWriter.drawChart(
					getDefaultOutputFile("-latency-evolution.png"), WIDTH,
					HEIGHT);
		}
	}

}