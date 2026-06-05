<#assign title="Predictions are open — get your picks in now!">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-window-open.jpg" width="600" alt="Predictions are open!" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">The Predictions Are Open, ${firstName}!</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 20px;">
              Submit your predictions before kickoff to score points. Miss the window and you miss the points.
            </p>
            <#if matches?has_content>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:24px;">
              <tr>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;">Match</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:right;">Kickoff</td>
              </tr>
              <#list matches as m>
              <tr style="background-color:<#if m?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:600;color:#0d1b2a;padding:10px 8px 10px 0;border-bottom:1px solid #e5e7eb;">${m.label}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#4a5568;padding:10px 0;border-bottom:1px solid #e5e7eb;text-align:right;">${m.kickoff}</td>
              </tr>
              </#list>
            </table>
            </#if>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">Predict Now</a>
                </td>
              </tr>
              <tr>
                <td align="center" style="padding:8px 0 0;">
                  <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#9ca3af;margin:0;">
                    Or visit: <a href="${appUrl}" style="color:#D4AF37;text-decoration:none;">${appUrl}</a>
                  </p>
                </td>
              </tr>
            </table>
          </td>
        </tr>
        <#include "_footer.ftlh">
      </table>
    </td>
  </tr>
</table>
</body>
</html>
