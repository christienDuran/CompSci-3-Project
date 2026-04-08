public class QuestionnaireEvent {
    String title;
    String day;
    String time; // will be implemented by Timeinterface
    String recurrence;

    public QuestionnaireEvent(String title, String day, String time, String recurrence){
        this.title = title;
        this.day = day;
        this.time = time;
        this.recurrence = recurrence;
    }
}
