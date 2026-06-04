<html>
<body style="margin:0;padding:0;background:#f0fdf5;">
<div style="max-width:560px;margin:32px auto;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);">
  <#include "_header.ftlh">
  <div style="padding:32px;">
    <h2 style="font-family:'Helvetica Neue',Arial,sans-serif;color:#006b2a;margin:0 0 16px;">Leaderboard Update</h2>
    <p style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;color:#333;line-height:1.6;">
      Hi ${firstName},
    </p>
    <p style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;color:#333;line-height:1.6;">
      All matches for today are complete. You're currently ranked <strong>#${rank}</strong> with <strong>${points} points</strong>.
    </p>

    <#if matchResults?has_content>
    <h3 style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:13px;color:#999;text-transform:uppercase;letter-spacing:1px;margin:24px 0 8px;">Today's Results</h3>
    <table style="width:100%;border-collapse:collapse;">
      <#list matchResults as r>
      <tr style="border-bottom:1px solid #e8f5e9;">
        <td style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:13px;padding:6px 0;color:#333;">${r.label}</td>
        <td style="font-family:'Courier New',monospace;font-size:13px;padding:6px 0;color:#333;font-weight:700;text-align:right;">${r.score}</td>
      </tr>
      </#list>
    </table>
    </#if>

    <h3 style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:13px;color:#999;text-transform:uppercase;letter-spacing:1px;margin:24px 0 8px;">Top 10</h3>
    <table style="width:100%;border-collapse:collapse;">
      <#list topEntries as e>
      <tr style="border-bottom:1px solid #e8f5e9;<#if e.rank == rank>background:#e8fdf1;</#if>">
        <td style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:13px;padding:6px 0;color:#999;width:30px;text-align:center;">${e.rank}</td>
        <td style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:13px;padding:6px 0;color:#333;font-weight:600;">${e.name}</td>
        <td style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:13px;padding:6px 0;color:#333;font-weight:700;text-align:right;">${e.points} pts</td>
      </tr>
      </#list>
    </table>

    <div style="text-align:center;margin:28px 0;">
      <a href="${appUrl}/leaderboard" style="display:inline-block;background:#00c853;color:#fff;font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;font-weight:700;padding:12px 32px;border-radius:8px;text-decoration:none;">
        View Full Leaderboard
      </a>
    </div>
  </div>
  <#include "_footer.ftlh">
</div>
</body>
</html>
