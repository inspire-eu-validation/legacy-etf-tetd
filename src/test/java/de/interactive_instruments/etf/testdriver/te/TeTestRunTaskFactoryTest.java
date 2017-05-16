/**
 * Copyright 2010-2017 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.testdriver.te;

import static de.interactive_instruments.etf.testdriver.te.TeTestDriver.TE_TEST_DRIVER_EID;
import static de.interactive_instruments.etf.testdriver.te.TeTestUtils.DATA_STORAGE;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import de.interactive_instruments.IFile;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.component.ComponentNotLoadedException;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dao.basex.BsxDataStorage;
import de.interactive_instruments.etf.dal.dto.capabilities.ResourceDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.run.TestRunDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.detector.TestObjectTypeDetectorManager;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.model.EidMap;
import de.interactive_instruments.etf.testdriver.*;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.PropertyUtils;

/**
 *
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TeTestRunTaskFactoryTest {

	// DO NOT RUN THE TESTS IN THE IDE BUT WITH GRADLE

	private TestDriverManager testDriverManager = null;

	private final static String VERSION = "1.26";
	private final static String LABEL = "WFS 2.0 (OGC 09-025r2/ISO 19142) Conformance Test Suite";
	private final static EID wfs20EtsId = EidFactory.getDefault().createUUID(
			"http://cite.opengeospatial.org/teamengine/rest/suites/wfs20/" + VERSION + "/" +
					LABEL);

	private WriteDao<ExecutableTestSuiteDto> etsDao() {
		return ((WriteDao) DATA_STORAGE.getDao(ExecutableTestSuiteDto.class));
	}

	private TestRunDto createTestRunDtoForProject(final String url)
			throws ComponentNotLoadedException, ConfigurationException, URISyntaxException,
			StorageException, ObjectWithIdNotFoundException, IOException {

		final TestObjectDto testObjectDto = new TestObjectDto();
		testObjectDto.setId(
				EidFactory.getDefault().createAndPreserveStr("fcfe9677-7b77-41dd-a17c-56884f60824f"));
		testObjectDto.setLabel("Cite 2013 WFS");
		final TestObjectTypeDto wfsTestObjectType = DATA_STORAGE.getDao(TestObjectTypeDto.class).getById(
				EidFactory.getDefault().createAndPreserveStr("9b6ef734-981e-4d60-aa81-d6730a1c6389")).getDto();
		testObjectDto.setTestObjectType(wfsTestObjectType);
		testObjectDto.addResource(new ResourceDto("serviceEndpoint", url));
		testObjectDto.setDescription("none");
		testObjectDto.setVersionFromStr("1.0.0");
		testObjectDto.setCreationDate(new Date(0));
		testObjectDto.setAuthor("ii");
		testObjectDto.setRemoteResource(URI.create("http://none"));
		testObjectDto.setItemHash(new byte[]{'0'});
		testObjectDto.setLocalPath("/none");
		try {
			((WriteDao) DATA_STORAGE.getDao(TestObjectDto.class)).delete(testObjectDto.getId());
		} catch (Exception e) {
			ExcUtils.suppress(e);
		}
		((WriteDao) DATA_STORAGE.getDao(TestObjectDto.class)).add(testObjectDto);

		final ExecutableTestSuiteDto ets = DATA_STORAGE.getDao(ExecutableTestSuiteDto.class).getById(wfs20EtsId).getDto();

		final TestTaskDto testTaskDto = new TestTaskDto();
		testTaskDto.setId(EidFactory.getDefault().createAndPreserveStr("aa03825a-2f64-4e52-bdba-90a08adb80ce"));
		testTaskDto.setExecutableTestSuite(ets);
		testTaskDto.setTestObject(testObjectDto);

		final TestRunDto testRunDto = new TestRunDto();
		testRunDto.setDefaultLang("en");
		testRunDto.setId(EidFactory.getDefault().createAndPreserveStr("7be08620-1805-4aca-840f-ac4dea2c4251"));
		testRunDto.setLabel("Run label");
		testRunDto.setStartTimestamp(new Date(0));
		testRunDto.addTestTask(testTaskDto);
		return testRunDto;
	}

	@Before
	public void setUp()
			throws IOException, ConfigurationException, InvalidStateTransitionException,
			InitializationException, ObjectWithIdNotFoundException, StorageException {

		// DO NOT RUN THE TESTS IN THE IDE BUT WITH GRADLE

		// Init logger
		LoggerFactory.getLogger(this.getClass()).info("Started");

		TeTestUtils.ensureInitialization();
		if (testDriverManager == null) {

			// Delete old ETS
			try {
				etsDao().delete(wfs20EtsId);
			} catch (final ObjectWithIdNotFoundException e) {
				ExcUtils.suppress(e);
			}

			final EidMap<TestObjectTypeDto> supportedTypes = TestObjectTypeDetectorManager.getSupportedTypes();
			((WriteDao) DATA_STORAGE.getDao(TestObjectTypeDto.class)).deleteAllExisting(supportedTypes.keySet());
			((WriteDao) DATA_STORAGE.getDao(TestObjectTypeDto.class)).addAll(supportedTypes.values());

			final IFile tdDir = new IFile(PropertyUtils.getenvOrProperty(
					"ETF_TD_DEPLOYMENT_DIR", "./build/tmp/td"));
			tdDir.expectDirIsReadable();

			// Load driver
			testDriverManager = new DefaultTestDriverManager();
			testDriverManager.getConfigurationProperties().setProperty(
					EtfConstants.ETF_TESTDRIVERS_DIR, tdDir.getAbsolutePath());
			final IFile attachmentDir = new IFile(PropertyUtils.getenvOrProperty(
					"ETF_DS_DIR", "./build/tmp/etf-ds")).secureExpandPathDown("attachments");
			attachmentDir.deleteDirectory();
			attachmentDir.mkdirs();
			testDriverManager.getConfigurationProperties().setProperty(
					EtfConstants.ETF_ATTACHMENT_DIR, attachmentDir.getAbsolutePath());
			testDriverManager.getConfigurationProperties().setProperty(
					EtfConstants.ETF_DATA_STORAGE_NAME,
					BsxDataStorage.class.getName());

			testDriverManager.init();
			testDriverManager.load(EidFactory.getDefault().createAndPreserveStr(TE_TEST_DRIVER_EID));
		}

	}

	@Test
	public void T1_checkInitializedEts() throws Exception, ComponentNotLoadedException {
		assertTrue(etsDao().exists(wfs20EtsId));

		final ExecutableTestSuiteDto ets = etsDao().getById(wfs20EtsId).getDto();
		assertEquals(LABEL, ets.getLabel());
		assertEquals(VERSION + ".0", ets.getVersionAsStr());
		assertEquals(0, ets.getAssertionsSize());
	}

	@Test
	public void T2_parseTestNgResults() throws Exception, ComponentNotLoadedException {
		final URL url = Thread.currentThread().getContextClassLoader().getResource("response.xml");
		final File file = new File(url.getPath());

		final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(true);
		final DocumentBuilder builder = domFactory.newDocumentBuilder();
		final Document result = builder.parse(file);

		final String testUrl = "https://services.interactive-instruments.de/cite-xs-46/simpledemo/cgi-bin/cities-postgresql/wfs?request=GetCapabilities&service=wfs";
		final TestRunDto testRunDto = createTestRunDtoForProject(testUrl);
		final TestRun testRun = testDriverManager.createTestRun(testRunDto);
		final TestTask task = testRun.getTestTasks().get(0);

		final Method method = task.getClass().getDeclaredMethod("parseTestNgResult", Document.class);
		method.setAccessible(true);
		method.invoke(task, result);
	}

	@Test(expected = ExecutionException.class)
	public void T3_runTestInvalidUrl() throws Exception, ComponentNotLoadedException {
		final String testUrl = "http://example.com";
		TestRunDto testRunDto = createTestRunDtoForProject(testUrl);

		final TestRun testRun = testDriverManager.createTestRun(testRunDto);
		final TaskPoolRegistry<TestRunDto, TestRun> taskPoolRegistry = new TaskPoolRegistry<>(1, 1);
		testRun.init();
		taskPoolRegistry.submitTask(testRun);
		taskPoolRegistry.getTaskById(testRunDto.getId()).waitForResult();
	}

	@Test
	public void T4_runTest() throws Exception, ComponentNotLoadedException {

		final String testUrl = "https://services.interactive-instruments.de/cite-xs-46/simpledemo/cgi-bin/cities-postgresql/wfs?request=GetCapabilities&service=wfs";
		TestRunDto testRunDto = createTestRunDtoForProject(testUrl);

		final TestRun testRun = testDriverManager.createTestRun(testRunDto);
		final TaskPoolRegistry<TestRunDto, TestRun> taskPoolRegistry = new TaskPoolRegistry<>(1, 1);
		testRun.init();
		taskPoolRegistry.submitTask(testRun);
		final TestRunDto runResult = taskPoolRegistry.getTaskById(testRunDto.getId()).waitForResult();

		assertNotNull(runResult);
		assertNotNull(runResult.getTestTaskResults());
		assertFalse(runResult.getTestTaskResults().isEmpty());
	}

}