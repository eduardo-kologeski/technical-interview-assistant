import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-identity-page', imports: [FormsModule, RouterLink],
  template: `<main class="identity"><section>
    @if (verifyMode) { <h1>Verificando e-mail</h1><p role="status">{{ message() }}</p> }
    @else if (resetMode) { <h1>Definir nova senha</h1><form (ngSubmit)="reset()"><label>Nova senha<input name="password" type="password" minlength="8" maxlength="72" [(ngModel)]="password" required></label><button>Atualizar senha</button></form><p role="status">{{ message() }}</p> }
    @else { <h1>Recuperar senha</h1><p>Se a conta existir, enviaremos as instruções.</p><form (ngSubmit)="forgot()"><label>E-mail<input name="email" type="email" [(ngModel)]="email" required></label><button>Enviar instruções</button></form><p role="status">{{ message() }}</p>@if (developmentPath()) { <a [routerLink]="developmentPath()">Abrir link local de recuperação</a> } }
    <p><a routerLink="/auth/login">Voltar ao login</a></p>
  </section></main>`,
  styles: [`:host{display:block;min-height:100vh;background:#eef4f5}.identity{min-height:100vh;display:grid;place-items:center;padding:24px}.identity section{width:min(430px,100%);box-sizing:border-box;background:#fff;padding:32px;border-radius:16px;box-shadow:0 18px 50px #1233}.identity form,.identity label{display:grid;gap:10px}.identity input,.identity button{padding:12px;border-radius:8px}.identity button{border:0;background:#165d7a;color:#fff;font-weight:800}`]
})
export class IdentityPageComponent {
  private auth=inject(AuthService); private route=inject(ActivatedRoute); private router=inject(Router);
  verifyMode=this.router.url.startsWith('/verify-email/'); resetMode=this.router.url.startsWith('/reset-password/');
  email=''; password=''; message=signal(this.verifyMode?'Validando...':''); developmentPath=signal<string|null>(null);
  constructor(){ if(this.verifyMode){this.auth.verifyEmail(this.route.snapshot.paramMap.get('token')||'').subscribe({next:()=>{this.message.set('E-mail verificado. Redirecionando...');this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('returnUrl')||'/dashboard')},error:()=>this.message.set('Link inválido ou expirado.')})} }
  forgot(){this.auth.forgotPassword(this.email).subscribe({next:r=>{this.message.set('Se a conta existir, as instruções foram enviadas.');this.developmentPath.set(r.developmentPath)},error:()=>this.message.set('Não foi possível concluir agora.')})}
  reset(){this.auth.resetPassword(this.route.snapshot.paramMap.get('token')||'',this.password).subscribe({next:()=>{this.message.set('Senha atualizada. Entre novamente.');setTimeout(()=>this.router.navigateByUrl('/auth/login'),800)},error:()=>this.message.set('Link inválido ou expirado.')})}
}
