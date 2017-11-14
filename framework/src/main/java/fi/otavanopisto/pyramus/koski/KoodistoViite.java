package fi.otavanopisto.pyramus.koski;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class KoodistoViite<T extends Enum<T>> {

  public KoodistoViite() {
  }
  
  public KoodistoViite(T koodiarvo) {
    this.koodiarvo = koodiarvo;
  }
  
  public void setValue(T koodiarvo) {
    this.koodiarvo = koodiarvo;
  }
  
  public String getKoodiarvo() {
    return koodiarvo != null ? koodiarvo.toString() : null;
  }
  
  public String getKoodistoUri() {
    if (koodiarvo != null) {
      KoodistoEnum annotation = koodiarvo.getClass().getAnnotation(KoodistoEnum.class);
      if (annotation != null)
        return annotation.value();
    }
    
    return null;
  }
  
  @JsonIgnore
  public T getValue() {
    return Enum.valueOf(koodiarvo.getDeclaringClass(), koodiarvo.name());
  }

  private Enum<T> koodiarvo;
}
