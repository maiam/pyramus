package fi.otavanopisto.pyramus.events;

public class StudentUpdatedEvent {

  public StudentUpdatedEvent(Long studentId) {
    super();
    this.studentId = studentId;
  }

  public Long getStudentId() {
    return studentId;
  }

  private Long studentId;
}
