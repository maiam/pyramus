package fi.pyramus.dao.users;

import java.util.List;
import java.util.Set;

import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Root;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.Version;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;

import fi.pyramus.dao.DAOFactory;
import fi.pyramus.dao.PyramusEntityDAO;
import fi.pyramus.domainmodel.base.BillingDetails;
import fi.pyramus.domainmodel.base.ContactInfo;
import fi.pyramus.domainmodel.base.ContactInfo_;
import fi.pyramus.domainmodel.base.Email;
import fi.pyramus.domainmodel.base.Email_;
import fi.pyramus.domainmodel.base.Person;
import fi.pyramus.domainmodel.base.Tag;
import fi.pyramus.domainmodel.users.Role;
import fi.pyramus.domainmodel.users.StaffMember;
import fi.pyramus.domainmodel.users.StaffMember_;
import fi.pyramus.domainmodel.users.User;
import fi.pyramus.domainmodel.users.UserVariable;
import fi.pyramus.domainmodel.users.UserVariableKey;
import fi.pyramus.domainmodel.users.UserVariable_;
import fi.pyramus.events.StaffMemberCreatedEvent;
import fi.pyramus.events.StaffMemberDeletedEvent;
import fi.pyramus.events.StaffMemberUpdatedEvent;
import fi.pyramus.persistence.search.SearchResult;

@Stateless
public class StaffMemberDAO extends PyramusEntityDAO<StaffMember> {
  
  @Inject
  private Event<StaffMemberCreatedEvent> staffMemberCreatedEvent;

  @Inject
  private Event<StaffMemberUpdatedEvent> staffMemberUpdatedEvent;
  
  @Inject
  private Event<StaffMemberDeletedEvent> staffMemberDeletedEvent;
  
  public StaffMember create(String firstName, String lastName, String externalId, String authProvider, Role role, Person person) {
    ContactInfo contactInfo = new ContactInfo();
    
    StaffMember staffMember = new StaffMember();

    staffMember.setFirstName(firstName);
    staffMember.setLastName(lastName);
    staffMember.setAuthProvider(authProvider);
    staffMember.setExternalId(externalId);
    staffMember.setRole(role);
    staffMember.setContactInfo(contactInfo);
    staffMember.setPerson(person);
    
    persist(staffMember);
    
    person.addUser(staffMember);
    getEntityManager().persist(person);
    
    staffMemberCreatedEvent.fire(new StaffMemberCreatedEvent(staffMember.getId()));

    return staffMember;
  }

  public List<User> listByUserVariable(String key, String value) {
    UserVariableKeyDAO variableKeyDAO = DAOFactory.getInstance().getUserVariableKeyDAO();
    UserVariableKey userVariableKey = variableKeyDAO.findByVariableKey(key);

    EntityManager entityManager = getEntityManager(); 
    
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<User> criteria = criteriaBuilder.createQuery(User.class);
    Root<UserVariable> root = criteria.from(UserVariable.class);
    criteria.select(root.get(UserVariable_.user));
    criteria.where(
        criteriaBuilder.and(
            criteriaBuilder.equal(root.get(UserVariable_.key), userVariableKey),
            criteriaBuilder.equal(root.get(UserVariable_.value), value)
        ));
    
    return entityManager.createQuery(criteria).getResultList();
  }

  public List<StaffMember> listByNotRole(Role role) {
    return listByNotRole(role, null, null);
  }

  public List<StaffMember> listByNotRole(Role role, Integer firstResult, Integer maxResults) {
    EntityManager entityManager = getEntityManager(); 
    
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<StaffMember> criteria = criteriaBuilder.createQuery(StaffMember.class);
    Root<StaffMember> root = criteria.from(StaffMember.class);
    criteria.select(root);
    
    criteria.where(
      criteriaBuilder.notEqual(root.get(StaffMember_.role), role)
    );
    
    TypedQuery<StaffMember> query = entityManager.createQuery(criteria);
    
    if (firstResult != null) {
      query.setFirstResult(firstResult);
    }
    
    if (maxResults != null) {
      query.setMaxResults(maxResults);
    }
    
    return query.getResultList();
  }
  
  public StaffMember findByExternalIdAndAuthProvider(String externalId, String authProvider) {
    EntityManager entityManager = getEntityManager(); 
    
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<StaffMember> criteria = criteriaBuilder.createQuery(StaffMember.class);
    Root<StaffMember> root = criteria.from(StaffMember.class);
    criteria.select(root);
    criteria.where(
        criteriaBuilder.and(
            criteriaBuilder.equal(root.get(StaffMember_.externalId), externalId),
            criteriaBuilder.equal(root.get(StaffMember_.authProvider), authProvider)
        ));
    
    return getSingleResult(entityManager.createQuery(criteria));
  }
  
  public StaffMember findByEmail(String email) {
    EntityManager entityManager = getEntityManager(); 
    
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<StaffMember> criteria = criteriaBuilder.createQuery(StaffMember.class);
    Root<StaffMember> root = criteria.from(StaffMember.class);
    
    Join<StaffMember, ContactInfo> contactInfoJoin = root.join(StaffMember_.contactInfo);
    ListJoin<ContactInfo, Email> emailJoin = contactInfoJoin.join(ContactInfo_.emails);
    
    criteria.select(root);
    criteria.where(
        criteriaBuilder.equal(emailJoin.get(Email_.address), email)
    );
    
    return getSingleResult(entityManager.createQuery(criteria));
  }


  public StaffMember findByPerson(Person person) {
    EntityManager entityManager = getEntityManager(); 
    
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<StaffMember> criteria = criteriaBuilder.createQuery(StaffMember.class);
    Root<StaffMember> root = criteria.from(StaffMember.class);
    criteria.select(root);
    criteria.where(
        criteriaBuilder.equal(root.get(StaffMember_.person), person)
    );
    
    return getSingleResult(entityManager.createQuery(criteria));
  }

  @SuppressWarnings("unchecked")
  public SearchResult<StaffMember> searchUsersBasic(int resultsPerPage, int page, String text) {

    int firstResult = page * resultsPerPage;

    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append("+(");
    addTokenizedSearchCriteria(queryBuilder, "firstName", text, false);
    addTokenizedSearchCriteria(queryBuilder, "lastName", text, false);
    addTokenizedSearchCriteria(queryBuilder, "tags.text", text, false);
    addTokenizedSearchCriteria(queryBuilder, "contactInfo.emails.address", text, false);
    queryBuilder.append(")");
  
    EntityManager entityManager = getEntityManager();
    FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(entityManager);

    try {
      String queryString = queryBuilder.toString();
      org.apache.lucene.search.Query luceneQuery;
      QueryParser parser = new QueryParser(Version.LUCENE_29, "", new StandardAnalyzer(Version.LUCENE_29));
      if (StringUtils.isBlank(queryString)) {
        luceneQuery = new MatchAllDocsQuery();
      }
      else {
        luceneQuery = parser.parse(queryString);
      }

      FullTextQuery query = (FullTextQuery) fullTextEntityManager.createFullTextQuery(luceneQuery, StaffMember.class)
          .setSort(new Sort(new SortField[] { SortField.FIELD_SCORE, new SortField("lastNameSortable", SortField.STRING),
                   new SortField("firstNameSortable", SortField.STRING) }))
          .setFirstResult(firstResult)
          . setMaxResults(resultsPerPage);

      int hits = query.getResultSize();
      int pages = hits / resultsPerPage;
      if (hits % resultsPerPage > 0) {
        pages++;
      }

      int lastResult = Math.min(firstResult + resultsPerPage, hits) - 1;

      return new SearchResult<StaffMember>(page, pages, hits, firstResult, lastResult, query.getResultList());

    } catch (ParseException e) {
      throw new PersistenceException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public SearchResult<StaffMember> searchUsers(int resultsPerPage, int page, String firstName, String lastName, String tags,
      String email, Role[] roles) {

    int firstResult = page * resultsPerPage;
    
    boolean hasFirstName = !StringUtils.isBlank(firstName);
    boolean hasLastName = !StringUtils.isBlank(lastName);
    boolean hasTags = !StringUtils.isBlank(tags);
    boolean hasEmail = !StringUtils.isBlank(email);
   
    StringBuilder queryBuilder = new StringBuilder();
    if (hasFirstName || hasLastName || hasEmail) {
      queryBuilder.append("+(");

      if (hasFirstName) 
        addTokenizedSearchCriteria(queryBuilder, "firstName", firstName, false);
      if (hasLastName)
        addTokenizedSearchCriteria(queryBuilder, "lastName", lastName, false);
      if (hasTags)
        addTokenizedSearchCriteria(queryBuilder, "tags.text", tags, false);
      if (hasEmail)
        addTokenizedSearchCriteria(queryBuilder, "contactInfo.emails.address", email, false);

      queryBuilder.append(")");
    }

    if (roles.length > 0) {
      queryBuilder.append("+(");
      for (Role role : roles) {
        addTokenizedSearchCriteria(queryBuilder, "role", role.toString(), false);
      }
      queryBuilder.append(")");
    }
    
    EntityManager entityManager = getEntityManager();
    FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(entityManager);

    try {
      String queryString = queryBuilder.toString();
      org.apache.lucene.search.Query luceneQuery;
      QueryParser parser = new QueryParser(Version.LUCENE_29, "", new StandardAnalyzer(Version.LUCENE_29));
      if (StringUtils.isBlank(queryString)) {
        luceneQuery = new MatchAllDocsQuery();
      }
      else {
        luceneQuery = parser.parse(queryString);
      }

      FullTextQuery query = (FullTextQuery) fullTextEntityManager.createFullTextQuery(luceneQuery, StaffMember.class)
          .setSort(new Sort(new SortField[] { SortField.FIELD_SCORE, new SortField("lastNameSortable", SortField.STRING),
                   new SortField("firstNameSortable", SortField.STRING) }))
          .setFirstResult(firstResult)
          .setMaxResults(resultsPerPage);

      int hits = query.getResultSize();
      int pages = hits / resultsPerPage;
      if (hits % resultsPerPage > 0) {
        pages++;
      }

      int lastResult = Math.min(firstResult + resultsPerPage, hits) - 1;

      return new SearchResult<StaffMember>(page, pages, hits, firstResult, lastResult, query.getResultList());

    } catch (ParseException e) {
      throw new PersistenceException(e);
    }
  }
  
  public StaffMember update(StaffMember staffMember, String firstName, String lastName, Role role) {
    staffMember.setFirstName(firstName);
    staffMember.setLastName(lastName);
    staffMember.setRole(role);
    persist(staffMember);
    
    staffMemberUpdatedEvent.fire(new StaffMemberUpdatedEvent(staffMember.getId()));
    
    return staffMember;
  }

  public StaffMember updateBillingDetails(StaffMember staffMember, List<BillingDetails> billingDetails) {
    staffMember.setBillingDetails(billingDetails);
    persist(staffMember);
    
    staffMemberUpdatedEvent.fire(new StaffMemberUpdatedEvent(staffMember.getId()));
    
    return staffMember;
  }
  
  public StaffMember updateTags(StaffMember staffMember, Set<Tag> tags) {
    staffMember.setTags(tags);
    persist(staffMember);
    
    staffMemberUpdatedEvent.fire(new StaffMemberUpdatedEvent(staffMember.getId()));
    
    return staffMember;
  }
  
  public StaffMember updateTitle(StaffMember staffMember, String title) {
    staffMember.setTitle(title);
    persist(staffMember);
    
    staffMemberUpdatedEvent.fire(new StaffMemberUpdatedEvent(staffMember.getId()));
    
    return staffMember;
  }

  public StaffMember updateAuthProvider(StaffMember staffMember, String authProvider) {
    staffMember.setAuthProvider(authProvider);
    persist(staffMember);

    staffMemberUpdatedEvent.fire(new StaffMemberUpdatedEvent(staffMember.getId()));
    
    return staffMember;
  }

  public StaffMember updateExternalId(StaffMember staffMember, String externalId) {
    staffMember.setExternalId(externalId);
    persist(staffMember);

    staffMemberUpdatedEvent.fire(new StaffMemberUpdatedEvent(staffMember.getId()));
    
    return staffMember;
  }

  @Override
  public void delete(StaffMember user) {
    Long id = user.getId();
    
    if (user.getPerson() != null)
      user.getPerson().removeUser(user);

    super.delete(user);
    
    staffMemberDeletedEvent.fire(new StaffMemberDeletedEvent(id));
  }

}