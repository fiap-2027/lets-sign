import { Injectable, Logger } from '@nestjs/common';
import { MailerService } from '@nestjs-modules/mailer';
import { ConfigService } from '@nestjs/config';
import { DocumentStatus, SignerStatus, RoleLabel } from '@prisma/client';

interface NotificationContext {
  nomeDestinatario: string;
  tituloDocumento: string;
  linkDocumento: string;
  nomeRemetente?: string;
  mensagem?: string;
  papel?: RoleLabel;
  motivoRecusa?: string;
  dataConclusao?: Date;
}

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);
  private readonly frontendUrl: string;

  constructor(
    private readonly mailer: MailerService,
    private readonly config: ConfigService,
  ) {
    this.frontendUrl = config.get<string>('FRONTEND_URL') || 'http://localhost:3000';
  }

  private getDocumentLink(documentId: string): string {
    return `${this.frontendUrl}/document/${documentId}`;
  }

  async sendDocumentInvite(
    to: string,
    context: NotificationContext,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: `📄 ${context.nomeRemetente || 'Alguém'} convidou você para assinar: ${context.tituloDocumento}`,
        template: 'document-invite',
        context: {
          ...context,
          linkDocumento: this.getDocumentLink(context.linkDocumento),
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`Convite enviado para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar convite para ${to}`, error.stack);
      throw error;
    }
  }

  async sendDocumentSigned(
    to: string,
    context: NotificationContext,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: `✅ Documento assinado: ${context.tituloDocumento}`,
        template: 'document-signed',
        context: {
          ...context,
          linkDocumento: this.getDocumentLink(context.linkDocumento),
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`Notificação de assinatura enviada para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar notificação de assinatura para ${to}`, error.stack);
      throw error;
    }
  }

  async sendDocumentCompleted(
    to: string,
    context: NotificationContext,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: `🎉 Documento concluído: ${context.tituloDocumento}`,
        template: 'document-completed',
        context: {
          ...context,
          linkDocumento: this.getDocumentLink(context.linkDocumento),
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`Notificação de conclusão enviada para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar notificação de conclusão para ${to}`, error.stack);
      throw error;
    }
  }

  async sendDocumentRejected(
    to: string,
    context: NotificationContext,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: `❌ Documento recusado: ${context.tituloDocumento}`,
        template: 'document-rejected',
        context: {
          ...context,
          linkDocumento: this.getDocumentLink(context.linkDocumento),
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`Notificação de recusa enviada para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar notificação de recusa para ${to}`, error.stack);
      throw error;
    }
  }

  async sendDocumentCancelled(
    to: string,
    context: NotificationContext,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: `⚠️ Documento cancelado: ${context.tituloDocumento}`,
        template: 'document-cancelled',
        context: {
          ...context,
          linkDocumento: this.getDocumentLink(context.linkDocumento),
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`Notificação de cancelamento enviada para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar notificação de cancelamento para ${to}`, error.stack);
      throw error;
    }
  }

  async sendNewVersion(
    to: string,
    context: NotificationContext,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: `🔄 Nova versão do documento: ${context.tituloDocumento}`,
        template: 'document-new-version',
        context: {
          ...context,
          linkDocumento: this.getDocumentLink(context.linkDocumento),
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`Notificação de nova versão enviada para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar notificação de nova versão para ${to}`, error.stack);
      throw error;
    }
  }

  async sendReminder(
    to: string,
    context: NotificationContext,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: `⏰ Lembrete: Assinatura pendente - ${context.tituloDocumento}`,
        template: 'document-reminder',
        context: {
          ...context,
          linkDocumento: this.getDocumentLink(context.linkDocumento),
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`Lembrete enviado para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar lembrete para ${to}`, error.stack);
      throw error;
    }
  }

  async sendWelcome(
    to: string,
    nome: string,
    senhaTemporaria?: string,
  ): Promise<void> {
    try {
      await this.mailer.sendMail({
        to,
        subject: '👋 Bem-vindo ao EasySign',
        template: 'welcome',
        context: {
          nome,
          senhaTemporaria,
          linkLogin: `${this.frontendUrl}/login`,
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`E-mail de boas-vindas enviado para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar e-mail de boas-vindas para ${to}`, error.stack);
      throw error;
    }
  }

  async sendPasswordReset(
    to: string,
    nome: string,
    resetToken: string,
  ): Promise<void> {
    try {
      const resetLink = `${this.frontendUrl}/reset-password?token=${resetToken}`;
      await this.mailer.sendMail({
        to,
        subject: '🔐 Redefinição de senha - EasySign',
        template: 'password-reset',
        context: {
          nome,
          resetLink,
          expiraEm: '1 hora',
          anoAtual: new Date().getFullYear(),
        },
      });
      this.logger.log(`E-mail de redefinição de senha enviado para ${to}`);
    } catch (error) {
      this.logger.error(`Falha ao enviar e-mail de redefinição de senha para ${to}`, error.stack);
      throw error;
    }
  }

  // Métodos de conveniência para integração com DocumentsService
  async notifyDocumentSent(
    signers: Array<{ email: string; nome: string; papel: RoleLabel }>,
    documentId: string,
    titulo: string,
    remetenteNome: string,
    mensagem?: string,
  ): Promise<void> {
    await Promise.all(
      signers.map((s) =>
        this.sendDocumentInvite(s.email, {
          nomeDestinatario: s.nome,
          tituloDocumento: titulo,
          linkDocumento: documentId,
          nomeRemetente: remetenteNome,
          mensagem,
          papel: s.papel,
        }),
      ),
    );
  }

  async notifySignerSigned(
    to: string,
    nome: string,
    documentId: string,
    titulo: string,
    signatarioNome: string,
  ): Promise<void> {
    await this.sendDocumentSigned(to, {
      nomeDestinatario: nome,
      tituloDocumento: titulo,
      linkDocumento: documentId,
      nomeRemetente: signatarioNome,
    });
  }

  async notifyDocumentCompleted(
    recipients: Array<{ email: string; nome: string }>,
    documentId: string,
    titulo: string,
  ): Promise<void> {
    await Promise.all(
      recipients.map((r) =>
        this.sendDocumentCompleted(r.email, {
          nomeDestinatario: r.nome,
          tituloDocumento: titulo,
          linkDocumento: documentId,
          dataConclusao: new Date(),
        }),
      ),
    );
  }

  async notifyDocumentRejected(
    to: string,
    nome: string,
    documentId: string,
    titulo: string,
    recusanteNome: string,
    motivo: string,
  ): Promise<void> {
    await this.sendDocumentRejected(to, {
      nomeDestinatario: nome,
      tituloDocumento: titulo,
      linkDocumento: documentId,
      nomeRemetente: recusanteNome,
      motivoRecusa: motivo,
    });
  }

  async notifyDocumentCancelled(
    recipients: Array<{ email: string; nome: string }>,
    documentId: string,
    titulo: string,
    motivo: string,
  ): Promise<void> {
    await Promise.all(
      recipients.map((r) =>
        this.sendDocumentCancelled(r.email, {
          nomeDestinatario: r.nome,
          tituloDocumento: titulo,
          linkDocumento: documentId,
          motivoRecusa: motivo,
        }),
      ),
    );
  }
}