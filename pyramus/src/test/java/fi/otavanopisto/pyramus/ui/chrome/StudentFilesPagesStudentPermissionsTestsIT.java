package fi.otavanopisto.pyramus.ui.chrome;

import org.junit.After;
import org.junit.Before;
import org.openqa.selenium.chrome.ChromeDriver;

import fi.otavanopisto.pyramus.ui.base.StudentsPagesStudentPermissionsTestsBase;

public class StudentFilesPagesStudentPermissionsTestsIT extends StudentsPagesStudentPermissionsTestsBase {
  
  @Before
  public void setUp() {
    setWebDriver(new ChromeDriver());
  }
  
  @After
  public void tearDown() {
    getWebDriver().quit();
  }
  
}
