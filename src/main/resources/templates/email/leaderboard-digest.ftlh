<#assign title="Leaderboard update — you're ranked #${rank}">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-leaderboard.jpg" width="600" alt="Leaderboard Update" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 8px;line-height:1.2;">You're Ranked #${rank}</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 24px;">
              Hi ${firstName} — you have <strong>${points} points</strong> after today's matches. Here's where things stand:
            </p>
            <#if matchResults?has_content>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;margin:0 0 8px;">Today's Results</p>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:28px;">
              <#list matchResults as r>
              <tr style="background-color:<#if r?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#0d1b2a;padding:9px 8px 9px 0;border-bottom:1px solid #e5e7eb;">${r.label}</td>
                <td style="font-family:Courier New,monospace;font-size:14px;font-weight:700;color:#0d1b2a;padding:9px 0;border-bottom:1px solid #e5e7eb;text-align:right;">${r.score}</td>
              </tr>
              </#list>
            </table>
            </#if>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;margin:0 0 8px;">Top 10 Standings</p>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:28px;">
              <tr>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;color:#9ca3af;padding:0 0 6px;border-bottom:2px solid #D4AF37;width:32px;text-align:center;">#</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;color:#9ca3af;padding:0 0 6px;border-bottom:2px solid #D4AF37;">Name</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;color:#9ca3af;padding:0 0 6px;border-bottom:2px solid #D4AF37;text-align:right;">Points</td>
              </tr>
              <#list topEntries as e>
              <tr style="background-color:<#if e.rank == rank>#FFF8E1<#elseif e?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:<#if e.rank == rank>#D4AF37<#else>#9ca3af</#if>;padding:9px 0;border-bottom:1px solid #e5e7eb;text-align:center;font-weight:<#if e.rank == rank>700<#else>400</#if>;">${e.rank}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#0d1b2a;padding:9px 8px;border-bottom:1px solid #e5e7eb;font-weight:<#if e.rank == rank>700<#else>400</#if>;">${e.name}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#0d1b2a;padding:9px 0;border-bottom:1px solid #e5e7eb;text-align:right;font-weight:700;">${e.points}</td>
              </tr>
              </#list>
            </table>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">View Leaderboard</a>
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
