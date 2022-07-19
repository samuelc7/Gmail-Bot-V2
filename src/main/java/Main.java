import App.HelloWorld;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.MessagingException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;

@RestController
public class Main {
    public static void main(String[] args) throws GeneralSecurityException, IOException, ParseException, MessagingException, InterruptedException {
        HelloWorld hw = new HelloWorld();
        hw.display();
        return;
    }
}
