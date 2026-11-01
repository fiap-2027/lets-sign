import { Test, TestingModule } from '@nestjs/testing';
import { NotificationsService } from '../../src/notifications/notifications.service';
import { MailerService } from '@nestjs-modules/mailer';
import { ConfigService } from '@nestjs/config';

const mockMailerService = {
  sendMail: jest.fn(),
};

const mockConfigService = {
  get: jest.fn((key: string) => {
    const config: Record<string, string> = {
      FRONTEND_URL: 'http://localhost:3000',
      SMTP_FROM: 'noreply@letssign.com',
    };
    return config[key];
  }),
};

describe('NotificationsService', () => {
  let service: NotificationsService;
  let mailer: typeof mockMailerService;

  beforeEach(async () => {
    jest.clearAllMocks();
    
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        NotificationsService,
        { provide: MailerService, useValue: mockMailerService },
        { provide: ConfigService, useValue: mockConfigService },
      ],
    }).compile();

    service = module.get<NotificationsService>(NotificationsService);
    mailer = mockMailerService;
  });

  describe('sendDocumentInvite', () => {
    it('deve enviar e-mail de convite com template correto', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.sendDocumentInvite('test@example.com', {
        nomeDestinatario: 'João Silva',
        tituloDocumento: 'Contrato de Prestação de Serviços',
        linkDocumento: 'doc-123',
        nomeRemetente: 'Maria Admin',
        mensagem: 'Por favor, assine este contrato.',
        papel: 'CONTRATADO',
      });

      expect(mailer.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          to: 'test@example.com',
          subject: expect.stringContaining('Contrato de Prestação de Serviços'),
          template: 'document-invite',
          context: expect.objectContaining({
            nomeDestinatario: 'João Silva',
            tituloDocumento: 'Contrato de Prestação de Serviços',
            nomeRemetente: 'Maria Admin',
            mensagem: 'Por favor, assine este contrato.',
            papel: 'CONTRATADO',
            linkDocumento: 'http://localhost:3000/document/doc-123',
            anoAtual: new Date().getFullYear(),
          }),
        })
      );
    });
  });

  describe('sendDocumentSigned', () => {
    it('deve enviar e-mail de notificação de assinatura', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.sendDocumentSigned('admin@example.com', {
        nomeDestinatario: 'Maria Admin',
        tituloDocumento: 'Contrato de Prestação de Serviços',
        linkDocumento: 'doc-123',
        nomeRemetente: 'João Silva',
      });

      expect(mailer.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          to: 'admin@example.com',
          subject: expect.stringContaining('assinado'),
          template: 'document-signed',
          context: expect.objectContaining({
            nomeDestinatario: 'Maria Admin',
            tituloDocumento: 'Contrato de Prestação de Serviços',
            nomeRemetente: 'João Silva',
          }),
        })
      );
    });
  });

  describe('sendDocumentCompleted', () => {
    it('deve enviar e-mail de conclusão para múltiplos destinatários', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.sendDocumentCompleted('test@example.com', {
        nomeDestinatario: 'João Silva',
        tituloDocumento: 'Contrato de Prestação de Serviços',
        linkDocumento: 'doc-123',
        dataConclusao: new Date('2024-01-15'),
      });

      expect(mailer.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          to: 'test@example.com',
          subject: expect.stringContaining('concluído'),
          template: 'document-completed',
          context: expect.objectContaining({
            nomeDestinatario: 'João Silva',
            tituloDocumento: 'Contrato de Prestação de Serviços',
            dataConclusao: new Date('2024-01-15'),
          }),
        })
      );
    });
  });

  describe('sendDocumentRejected', () => {
    it('deve enviar e-mail de recusa com motivo', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.sendDocumentRejected('admin@example.com', {
        nomeDestinatario: 'Maria Admin',
        tituloDocumento: 'Contrato de Prestação de Serviços',
        linkDocumento: 'doc-123',
        nomeRemetente: 'João Silva',
        motivoRecusa: 'Cláusula 5 incorreta',
      });

      expect(mailer.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          to: 'admin@example.com',
          subject: expect.stringContaining('recusado'),
          template: 'document-rejected',
          context: expect.objectContaining({
            motivoRecusa: 'Cláusula 5 incorreta',
          }),
        })
      );
    });
  });

  describe('sendWelcome', () => {
    it('deve enviar e-mail de boas-vindas com senha temporária', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.sendWelcome('newuser@example.com', 'Novo Usuário', 'TempPass123!');

      expect(mailer.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          to: 'newuser@example.com',
          subject: '👋 Bem-vindo ao EasySign',
          template: 'welcome',
          context: expect.objectContaining({
            nome: 'Novo Usuário',
            senhaTemporaria: 'TempPass123!',
            linkLogin: 'http://localhost:3000/login',
          }),
        })
      );
    });

    it('deve enviar e-mail de boas-vindas sem senha temporária', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.sendWelcome('newuser@example.com', 'Novo Usuário');

      expect(mailer.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          context: expect.not.objectContaining({
            senhaTemporaria: expect.any(String),
          }),
        })
      );
    });
  });

  describe('sendPasswordReset', () => {
    it('deve enviar e-mail de redefinição de senha', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.sendPasswordReset('user@example.com', 'Usuário Teste', 'reset-token-123');

      expect(mailer.sendMail).toHaveBeenCalledWith(
        expect.objectContaining({
          to: 'user@example.com',
          subject: '🔐 Redefinição de senha - EasySign',
          template: 'password-reset',
          context: expect.objectContaining({
            nome: 'Usuário Teste',
            resetLink: 'http://localhost:3000/reset-password?token=reset-token-123',
            expiraEm: '1 hora',
          }),
        })
      );
    });
  });

  describe('notifyDocumentSent', () => {
    it('deve enviar notificações para múltiplos signatários', async () => {
      mailer.sendMail.mockResolvedValue({});

      await service.notifyDocumentSent(
        [
          { email: 'signer1@example.com', nome: 'Signer 1', papel: 'CONTRATANTE' },
          { email: 'signer2@example.com', nome: 'Signer 2', papel: 'CONTRATADO' },
        ],
        'doc-123',
        'Contrato Teste',
        'Admin Teste',
        'Mensagem de teste'
      );

      expect(mailer.sendMail).toHaveBeenCalledTimes(2);
    });
  });
});