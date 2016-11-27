package name.treskunov.denis.easyssl.server;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    
    @RequestMapping("/")
    public String index(@AuthenticationPrincipal UserDetails user) {
        String name = user == null ? "stranger" : user.getUsername();
        return String.format("Hello, %s!", name);
    }

    @PreAuthorize("isAuthenticated()")
    @RequestMapping("/whoami")
    public String whoami(Authentication auth) {
        return auth.toString();
    }
}
