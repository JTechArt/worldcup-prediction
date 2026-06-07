<#assign title="Your predictions have been submitted">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">Predictions Confirmed, ${firstName}!</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 8px;">
              Your predictions for <strong>${roundLabel}</strong> have been recorded.
            </p>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#9ca3af;line-height:1.5;margin:0 0 24px;">
              Submitted at: ${submittedAt}
            </p>
            <#if predictions?has_content>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:24px;">
              <tr>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;">Home</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:center;">Score</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:right;">Away</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:right;">Kickoff</td>
              </tr>
              <#list predictions as p>
              <tr style="background-color:<#if p?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:600;color:#0d1b2a;padding:10px 8px 10px 0;border-bottom:1px solid #e5e7eb;">${p.homeTeam}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:700;color:#0d1b2a;padding:10px 4px;border-bottom:1px solid #e5e7eb;text-align:center;">${p.predictedHome} - ${p.predictedAway}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:600;color:#0d1b2a;padding:10px 0 10px 8px;border-bottom:1px solid #e5e7eb;text-align:right;">${p.awayTeam}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#4a5568;padding:10px 0;border-bottom:1px solid #e5e7eb;text-align:right;">${p.kickoff}</td>
              </tr>
              </#list>
            </table>
            </#if>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#6b7280;line-height:1.5;margin:0 0 24px;padding:12px 16px;background-color:#f9fafb;border-left:3px solid #D4AF37;border-radius:4px;">
              This email serves as your official confirmation of submitted predictions. Keep it for your records.
            </p>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">View My Predictions</a>
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
