package fi.otavanopisto.pyramus.views.applications;

import fi.internetix.smvc.controllers.PageRequestContext;
import fi.otavanopisto.pyramus.dao.DAOFactory;
import fi.otavanopisto.pyramus.dao.application.ApplicationNotificationDAO;
import fi.otavanopisto.pyramus.domainmodel.application.ApplicationNotification;
import fi.otavanopisto.pyramus.domainmodel.users.User;
import fi.otavanopisto.pyramus.framework.PyramusViewController;
import fi.otavanopisto.pyramus.framework.UserRole;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class EditNotificationViewController extends PyramusViewController {
  
  public void process(PageRequestContext pageRequestContext) {
    ApplicationNotificationDAO applicationNotificationDAO = DAOFactory.getInstance().getApplicationNotificationDAO();
    ApplicationNotification applicationNotification = applicationNotificationDAO.findById(pageRequestContext.getLong("notification"));
    pageRequestContext.getRequest().setAttribute("notification", applicationNotification);
    JSONArray usersJSON = new JSONArray();
    for (User user : applicationNotification.getUsers()) {
      JSONObject obj = new JSONObject();
      obj.put("id", user.getId());
      obj.put("name",  user.getFullName());
      usersJSON.add(obj);
    }
    setJsDataVariable(pageRequestContext, "users", usersJSON.toString());
    pageRequestContext.setIncludeJSP("/templates/applications/editnotification.jsp");
  }

  public UserRole[] getAllowedRoles() {
    return new UserRole[] { UserRole.ADMINISTRATOR, UserRole.MANAGER, UserRole.STUDY_PROGRAMME_LEADER };
  }

}
