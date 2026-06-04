<html>
<body style="margin:0;padding:0;background:#f0fdf5;">
<div style="max-width:560px;margin:32px auto;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);">
  <#include "_header.ftlh">
  <div style="padding:32px;">
    <h2 style="font-family:'Helvetica Neue',Arial,sans-serif;color:#00c853;margin:0 0 16px;">Predictions are open!</h2>
    <p style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;color:#333;line-height:1.6;">
      Hi ${firstName},
    </p>
    <p style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;color:#333;line-height:1.6;">
      The prediction window is now open for the following matches:
    </p>
    <table style="width:100%;border-collapse:collapse;margin:16px 0;">
      <#list matches as m>
      <tr style="border-bottom:1px solid #e8f5e9;">
        <td style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:13px;padding:8px 0;color:#333;font-weight:600;">${m.label}</td>
        <td style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:12px;padding:8px 0;color:#999;text-align:right;">${m.kickoff}</td>
      </tr>
      </#list>
    </table>
    <div style="text-align:center;margin:28px 0;">
      <a href="${appUrl}/predictions" style="display:inline-block;background:#00c853;color:#fff;font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;font-weight:700;padding:12px 32px;border-radius:8px;text-decoration:none;">
        Make Your Predictions
      </a>
    </div>
  </div>
  <#include "_footer.ftlh">
</div>
</body>
</html>
