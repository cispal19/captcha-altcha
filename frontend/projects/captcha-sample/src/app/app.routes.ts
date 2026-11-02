import { Routes } from '@angular/router';
import { ConsultaComponent } from './consulta/consulta.component';
import { LoginComponent } from './login/login.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: 'login', component: LoginComponent, title: 'Ingreso' },
  { path: 'consulta', component: ConsultaComponent, title: 'Consulta pública' },
];
