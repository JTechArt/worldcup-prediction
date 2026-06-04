<html>
<body style="margin:0;padding:0;background:#f0fdf5;">
<div style="max-width:560px;margin:32px auto;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);">
  <#include "_header.ftlh">
  <div style="padding:32px;">
    <h2 style="font-family:'Helvetica Neue',Arial,sans-serif;color:#006b2a;margin:0 0 16px;">You're in!</h2>
    <p style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;color:#333;line-height:1.6;">
      Hi ${firstName},
    </p>
    <p style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;color:#333;line-height:1.6;">
      Your registration for the <strong>World Cup 2026 Prediction Game</strong> has been approved.
    </p>
    <p style="font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;color:#333;line-height:1.6;">
      Sign in with your Google or LinkedIn account to start making predictions.
    </p>
    <div style="text-align:center;margin:28px 0;">
      <a href="${appUrl}" style="display:inline-block;background:#00c853;color:#fff;font-family:'Helvetica Neue',Arial,sans-serif;font-size:14px;font-weight:700;padding:12px 32px;border-radius:8px;text-decoration:none;">
        Start Predicting
      </a>
    </div>
  </div>
  <#include "_footer.ftlh">
</div>
</body>
</html>
