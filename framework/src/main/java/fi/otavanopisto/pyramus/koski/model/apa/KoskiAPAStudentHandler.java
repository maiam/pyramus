package fi.otavanopisto.pyramus.koski.model.apa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

import fi.otavanopisto.pyramus.domainmodel.base.EducationType;
import fi.otavanopisto.pyramus.domainmodel.base.Subject;
import fi.otavanopisto.pyramus.domainmodel.koski.KoskiPersonState;
import fi.otavanopisto.pyramus.domainmodel.students.Student;
import fi.otavanopisto.pyramus.domainmodel.students.StudentLodgingPeriod;
import fi.otavanopisto.pyramus.koski.CreditStub;
import fi.otavanopisto.pyramus.koski.CreditStubCredit;
import fi.otavanopisto.pyramus.koski.KoodistoViite;
import fi.otavanopisto.pyramus.koski.KoskiConsts;
import fi.otavanopisto.pyramus.koski.KoskiStudentHandler;
import fi.otavanopisto.pyramus.koski.KoskiStudentId;
import fi.otavanopisto.pyramus.koski.KoskiStudyProgrammeHandler;
import fi.otavanopisto.pyramus.koski.OpiskelijanOPS;
import fi.otavanopisto.pyramus.koski.StudentSubjectSelections;
import fi.otavanopisto.pyramus.koski.CreditStubCredit.Type;
import fi.otavanopisto.pyramus.koski.koodisto.AikuistenPerusopetuksenAlkuvaiheenKurssit2017;
import fi.otavanopisto.pyramus.koski.koodisto.AikuistenPerusopetuksenAlkuvaiheenOppiaineet;
import fi.otavanopisto.pyramus.koski.koodisto.ArviointiasteikkoYleissivistava;
import fi.otavanopisto.pyramus.koski.koodisto.Kieli;
import fi.otavanopisto.pyramus.koski.koodisto.Kielivalikoima;
import fi.otavanopisto.pyramus.koski.koodisto.OpintojenRahoitus;
import fi.otavanopisto.pyramus.koski.koodisto.OpiskeluoikeudenTila;
import fi.otavanopisto.pyramus.koski.koodisto.OppiaineAidinkieliJaKirjallisuus;
import fi.otavanopisto.pyramus.koski.koodisto.PerusopetuksenSuoritusTapa;
import fi.otavanopisto.pyramus.koski.koodisto.SuorituksenTila;
import fi.otavanopisto.pyramus.koski.model.KurssinArviointi;
import fi.otavanopisto.pyramus.koski.model.KurssinArviointiNumeerinen;
import fi.otavanopisto.pyramus.koski.model.KurssinArviointiSanallinen;
import fi.otavanopisto.pyramus.koski.model.Majoitusjakso;
import fi.otavanopisto.pyramus.koski.model.Opiskeluoikeus;
import fi.otavanopisto.pyramus.koski.model.OpiskeluoikeusJakso;
import fi.otavanopisto.pyramus.koski.model.OrganisaationToimipiste;
import fi.otavanopisto.pyramus.koski.model.OrganisaationToimipisteOID;
import fi.otavanopisto.pyramus.koski.model.OsaamisenTunnustaminen;
import fi.otavanopisto.pyramus.koski.model.PaikallinenKoodi;
import fi.otavanopisto.pyramus.koski.model.aikuistenperusopetus.AikuistenPerusopetuksenOpiskeluoikeudenLisatiedot;
import fi.otavanopisto.pyramus.koski.model.aikuistenperusopetus.AikuistenPerusopetuksenOpiskeluoikeus;

public class KoskiAPAStudentHandler extends KoskiStudentHandler {

  private static final KoskiStudyProgrammeHandler HANDLER_TYPE = KoskiStudyProgrammeHandler.aikuistenperusopetuksenalkuvaihe;
  
  @Inject
  private Logger logger;

  public Opiskeluoikeus studentToModel(Student student, String academyIdentifier) {
    StudentSubjectSelections studentSubjects = loadStudentSubjectSelections(student, getDefaultStudentSubjectSelections());
    String studyOid = userVariableDAO.findByUserAndKey(student, KOSKI_STUDYPERMISSION_ID);

    // Skip student if it is archived and the studyoid is blank
    if (Boolean.TRUE.equals(student.getArchived()) && StringUtils.isBlank(studyOid)) {
      return null;
    }
    
    OpiskelijanOPS ops = resolveOPS(student);
    if (ops == null) {
      koskiPersonLogDAO.create(student.getPerson(), student, KoskiPersonState.NO_CURRICULUM, new Date());
      return null;
    }
    
    AikuistenPerusopetuksenOpiskeluoikeus opiskeluoikeus = new AikuistenPerusopetuksenOpiskeluoikeus();
    opiskeluoikeus.setLahdejarjestelmanId(getLahdeJarjestelmaID(HANDLER_TYPE, student.getId()));
    opiskeluoikeus.setAlkamispaiva(student.getStudyStartDate());
    opiskeluoikeus.setPaattymispaiva(student.getStudyEndDate());
    if (StringUtils.isNotBlank(studyOid)) {
      opiskeluoikeus.setOid(studyOid);
    }
    
    opiskeluoikeus.setLisatiedot(getLisatiedot(student));
    
    OpiskeluoikeudenTila jaksonTila = !Boolean.TRUE.equals(student.getArchived()) ? OpiskeluoikeudenTila.lasna : OpiskeluoikeudenTila.mitatoity;
    OpiskeluoikeusJakso jakso = new OpiskeluoikeusJakso(student.getStudyStartDate(), jaksonTila);
    jakso.setOpintojenRahoitus(new KoodistoViite<>(student.getSchool() == null ? OpintojenRahoitus.K1 : OpintojenRahoitus.K6));
    opiskeluoikeus.getTila().addOpiskeluoikeusJakso(jakso);

    SuorituksenTila suorituksenTila = SuorituksenTila.KESKEN;

    if (student.getStudyEndDate() != null) {
      OpiskeluoikeudenTila opintojenLopetusTila = settings.getStudentState(student, OpiskeluoikeudenTila.eronnut);
      opiskeluoikeus.getTila().addOpiskeluoikeusJakso(
          new OpiskeluoikeusJakso(student.getStudyEndDate(), opintojenLopetusTila));

      suorituksenTila = ArrayUtils.contains(OpiskeluoikeudenTila.GRADUATED_STATES, opintojenLopetusTila) ? 
          SuorituksenTila.VALMIS : SuorituksenTila.KESKEYTYNYT;
    }
    
    OrganisaationToimipiste toimipiste = new OrganisaationToimipisteOID(academyIdentifier);
    APASuoritus suoritus = new APASuoritus(
        PerusopetuksenSuoritusTapa.koulutus, Kieli.FI, toimipiste);
    suoritus.setTodistuksellaNakyvatLisatiedot(getTodistuksellaNakyvatLisatiedot(student));
    suoritus.getKoulutusmoduuli().setPerusteenDiaarinumero(getDiaarinumero(student));
    if (suorituksenTila == SuorituksenTila.VALMIS)
      suoritus.setVahvistus(getVahvistus(student, academyIdentifier));
    opiskeluoikeus.addSuoritus(suoritus);
    
    EducationType studentEducationType = student.getStudyProgramme() != null && student.getStudyProgramme().getCategory() != null ? 
        student.getStudyProgramme().getCategory().getEducationType() : null;
    assessmentsToModel(ops, student, studentEducationType, studentSubjects, suoritus, suorituksenTila == SuorituksenTila.VALMIS);
    
    return opiskeluoikeus;
  }
  
  private AikuistenPerusopetuksenOpiskeluoikeudenLisatiedot getLisatiedot(Student student) {
    AikuistenPerusopetuksenOpiskeluoikeudenLisatiedot lisatiedot = new AikuistenPerusopetuksenOpiskeluoikeudenLisatiedot();
    
    lisatiedot.setVuosiluokkiinSitoutumatonOpetus(true);
    
    List<StudentLodgingPeriod> lodgingPeriods = lodgingPeriodDAO.listByStudent(student);
    for (StudentLodgingPeriod lodgingPeriod : lodgingPeriods) {
      lisatiedot.addSisaoppilaitosmainenMajoitus(new Majoitusjakso(lodgingPeriod.getBegin(), lodgingPeriod.getEnd()));
    }
    
    if (!lodgingPeriods.isEmpty()) {
      Date minBeginDate = lodgingPeriods.stream().map(StudentLodgingPeriod::getBegin).filter(Objects::nonNull)
          .min(Comparator.nullsLast(Date::compareTo)).orElse(null);
      
      if (minBeginDate != null) {
        Date maxEndDate = lodgingPeriods.stream().map(StudentLodgingPeriod::getEnd).filter(Objects::nonNull)
            .max(Comparator.nullsLast(Date::compareTo)).orElse(null);
        
        lisatiedot.setOikeusMaksuttomaanAsuntolapaikkaan(new Majoitusjakso(minBeginDate, maxEndDate));
      }
    }
    
    return lisatiedot;
  }

  private StudentSubjectSelections getDefaultStudentSubjectSelections() {
    StudentSubjectSelections studentSubjects = new StudentSubjectSelections();
    studentSubjects.setA1Languages("aena");
    return studentSubjects;
  }

  private void assessmentsToModel(OpiskelijanOPS ops, Student student, EducationType studentEducationType, StudentSubjectSelections studentSubjects,
      APASuoritus oppimaaranSuoritus, boolean calculateMeanGrades) {
    Collection<CreditStub> credits = listCredits(student, true, true, ops, credit -> matchingCurriculumFilter(student, credit));
    
    Map<String, APAOppiaineenSuoritus> map = new HashMap<>();
    Set<APAOppiaineenSuoritus> accomplished = new HashSet<>();
    
    for (CreditStub credit : credits) {
      APAOppiaineenSuoritus oppiaineenSuoritus = getSubject(student, studentEducationType, studentSubjects, credit.getSubject(), map);
      collectAccomplishedMarks(credit.getSubject(), oppiaineenSuoritus, studentSubjects, accomplished);

      if (settings.isReportedCredit(credit) && oppiaineenSuoritus != null) {
        APAKurssinSuoritus kurssiSuoritus = createKurssiSuoritus(ops, credit);
        if (kurssiSuoritus != null) {
          oppiaineenSuoritus.addOsasuoritus(kurssiSuoritus);
        } else {
          logger.warning(String.format("Course %s not reported for student %d due to unresolvable credit.", credit.getCourseCode(), student.getId()));
          koskiPersonLogDAO.create(student.getPerson(), student, KoskiPersonState.UNREPORTED_CREDIT, new Date(), credit.getCourseCode());
        }
      }
    }
    
    for (APAOppiaineenSuoritus oppiaineenSuoritus : map.values()) {
      if (CollectionUtils.isEmpty(oppiaineenSuoritus.getOsasuoritukset())) {
        // Skip empty subjects
        continue;
      }
      
      // Valmiille oppiaineelle on rustattava kokonaisarviointi
      if (calculateMeanGrades) {
        ArviointiasteikkoYleissivistava aineKeskiarvo = accomplished.contains(oppiaineenSuoritus) ? 
            ArviointiasteikkoYleissivistava.GRADE_S : getSubjectMeanGrade(oppiaineenSuoritus);
        
        if (ArviointiasteikkoYleissivistava.isNumeric(aineKeskiarvo)) {
          KurssinArviointi arviointi = new KurssinArviointiNumeerinen(aineKeskiarvo, student.getStudyEndDate());
          oppiaineenSuoritus.addArviointi(arviointi);
        } else if (ArviointiasteikkoYleissivistava.isLiteral(aineKeskiarvo)) {
          KurssinArviointi arviointi = new KurssinArviointiSanallinen(aineKeskiarvo, student.getStudyEndDate(), kuvaus("Suoritettu/Hylätty"));
          oppiaineenSuoritus.addArviointi(arviointi);
        }
      }
      
      oppimaaranSuoritus.addOsasuoritus(oppiaineenSuoritus);
    }
  }

  private APAOppiaineenSuoritus getSubject(Student student, EducationType studentEducationType, 
      StudentSubjectSelections studentSubjects, Subject subject, Map<String, APAOppiaineenSuoritus> map) {
    String subjectCode = subjectCode(subject);

    if (map.containsKey(subjectCode)) {
      return map.get(subjectCode);
    }
    
    boolean matchingEducationType = studentEducationType != null && subject.getEducationType() != null && 
        studentEducationType.getId().equals(subject.getEducationType().getId());
    
    if (matchingEducationType && StringUtils.equals(subjectCode, "as2")) {
      OppiaineAidinkieliJaKirjallisuus aine = OppiaineAidinkieliJaKirjallisuus.AI7; // s2
      
      APAOppiaineenTunniste tunniste = new APAOppiaineenTunnisteAidinkieli(aine);
      APAOppiaineenSuoritus os = new APAOppiaineenSuoritus(tunniste);
      map.put(subjectCode, os);
      return os;
    }
    
    // APA has only A1 foreign language
    if (matchingEducationType && studentSubjects.isA1Language(subjectCode)) {
      if (subjectCode.length() >= 3) {
        // APA Subject codes are of form a + code so substring without the a
        String langCode = settings.getSubjectToLanguageMapping(subjectCode.substring(1, 3).toUpperCase());
        Kielivalikoima kieli = Kielivalikoima.valueOf(langCode);
        
        if (kieli != null) {
          APAOppiaineenTunniste tunniste = new APAOppiaineenTunnisteVierasKieli(
              AikuistenPerusopetuksenAlkuvaiheenOppiaineet.A1, kieli);
          APAOppiaineenSuoritus os = new APAOppiaineenSuoritus(tunniste);
          map.put(subjectCode, os);
          return os;
        } else {
          logger.log(Level.SEVERE, String.format("Koski: Language code %s could not be converted to an enum.", langCode));
          koskiPersonLogDAO.create(student.getPerson(), student, KoskiPersonState.UNKNOWN_LANGUAGE, new Date(), langCode);
          return null;
        }
      }
    }

    AikuistenPerusopetuksenAlkuvaiheenOppiaineet nationalSubject = getNationalSubject(subjectCode);
    
    if (nationalSubject != null) {
      // Common national subject
      
      APAOppiaineenTunniste tunniste = new APAOppiaineenTunnisteMuu(nationalSubject);
      APAOppiaineenSuoritus os = new APAOppiaineenSuoritus(tunniste);
      map.put(subjectCode, os);
      return os;
    } else {
      // Other local subject
      
      PaikallinenKoodi paikallinenKoodi = new PaikallinenKoodi(subjectCode, kuvaus(subject.getName()));
      APAOppiaineenTunniste tunniste = new APAOppiaineenTunnistePaikallinen(paikallinenKoodi, kuvaus(subject.getName()));
      APAOppiaineenSuoritus os = new APAOppiaineenSuoritus(tunniste);
      map.put(subjectCode, os);
      return os;
    }
  }
  
  private AikuistenPerusopetuksenAlkuvaiheenOppiaineet getNationalSubject(String subjectCode) {
    subjectCode = subjectCode.toUpperCase();
    
    for (AikuistenPerusopetuksenAlkuvaiheenOppiaineet subject : AikuistenPerusopetuksenAlkuvaiheenOppiaineet.values()) {
      if (subjectCode.equals("A" + subject.toString())) {
        return subject;
      }
    }
    
    return null;
  }

  protected APAKurssinSuoritus createKurssiSuoritus(OpiskelijanOPS ops, CreditStub courseCredit) {
    String kurssiKoodi = StringUtils.upperCase(courseCredit.getCourseCode());
    APAKurssinTunniste tunniste;
    
    if (ops == OpiskelijanOPS.ops2018 && EnumUtils.isValidEnum(AikuistenPerusopetuksenAlkuvaiheenKurssit2017.class, kurssiKoodi)) {
      AikuistenPerusopetuksenAlkuvaiheenKurssit2017 kurssi = AikuistenPerusopetuksenAlkuvaiheenKurssit2017.valueOf(kurssiKoodi);
      tunniste = new APAKurssinTunnisteOPS2017(kurssi);
    } else {
      PaikallinenKoodi paikallinenKoodi = new PaikallinenKoodi(kurssiKoodi, kuvaus(courseCredit.getSubject().getName()));
      tunniste = new APAKurssinTunnistePaikallinen(paikallinenKoodi);
    }
      
    APAKurssinSuoritus suoritus = new APAKurssinSuoritus(tunniste);

    // Hyväksilukutieto on hölmössä paikassa; jos kaikki arvosanat ovat hyväksilukuja, tallennetaan 
    // tieto hyväksilukuna - ongelmallista, jos hyväksiluettua kurssia on korotettu 
    if (courseCredit.getCredits().stream().allMatch(credit -> credit.getType() == Type.RECOGNIZED)) {
      OsaamisenTunnustaminen tunnustettu = new OsaamisenTunnustaminen(kuvaus("Hyväksiluku"));
      suoritus.setTunnustettu(tunnustettu);
    }

    for (CreditStubCredit credit : courseCredit.getCredits()) {
      ArviointiasteikkoYleissivistava arvosana = getArvosana(credit.getGrade());

      KurssinArviointi arviointi = null;
      if (ArviointiasteikkoYleissivistava.isNumeric(arvosana)) {
        arviointi =  new KurssinArviointiNumeerinen(arvosana, credit.getDate());
      } else if (ArviointiasteikkoYleissivistava.isLiteral(arvosana)) {
        arviointi = new KurssinArviointiSanallinen(arvosana, credit.getDate(), kuvaus(credit.getGrade().getName()));
      }

      if (arviointi != null) {
        suoritus.addArviointi(arviointi);
      }
    }
    
    // Don't report the course if there's no credits
    if (CollectionUtils.isEmpty(suoritus.getArviointi())) {
      return null;
    }
  
    return suoritus;
  }

  private ArviointiasteikkoYleissivistava getSubjectMeanGrade(APAOppiaineenSuoritus oppiaineenSuoritus) {
    List<ArviointiasteikkoYleissivistava> kurssiarvosanat = new ArrayList<>();
    for (APAKurssinSuoritus kurssinSuoritus : oppiaineenSuoritus.getOsasuoritukset()) {
      List<KurssinArviointi> arvioinnit = kurssinSuoritus.getArviointi();
      Set<ArviointiasteikkoYleissivistava> arvosanat = arvioinnit.stream().map(arviointi -> arviointi.getArvosana().getValue()).collect(Collectors.toSet());
      
      kurssiarvosanat.add(ArviointiasteikkoYleissivistava.bestGrade(arvosanat));
    }
    
    return ArviointiasteikkoYleissivistava.meanGrade(kurssiarvosanat);
  }

  @Override
  public void saveOrValidateOid(KoskiStudyProgrammeHandler handler, Student student, String oid) {
    saveOrValidateOid(student, oid);
  }
  
  @Override
  public Set<KoskiStudentId> listOids(Student student) {
    String oid = userVariableDAO.findByUserAndKey(student, KoskiConsts.VariableNames.KOSKI_STUDYPERMISSION_ID);
    if (StringUtils.isNotBlank(oid)) {
      return new HashSet<>(Arrays.asList(new KoskiStudentId(getStudentIdentifier(HANDLER_TYPE, student.getId()), oid)));
    } else {
      return new HashSet<>();
    }
  }
}
